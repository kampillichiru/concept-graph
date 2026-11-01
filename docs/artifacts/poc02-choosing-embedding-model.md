# POC 2 artifact: how to choose an embedding model

One page for a customer conversation: *"Which embedding model should we use?"*
Measured numbers come from `GET /poc02/embedding-stats` on the project laptop (CPU only, Ollama).

## Measured: nomic-embed-text on this laptop

| Metric | Value |
|---|---|
| Dimensions | 768 |
| Vector length | 1.0 (normalised, so cosine = dot product) |
| First call (may include loading the model) | 1,453 ms |
| One text, warm | ~87 ms |
| 15 texts one by one | 1,312 ms |
| 15 texts in one batch (`embedAll`) | 848 ms: **~35% faster** |
| Size per vector (float32) | 3,072 bytes (~3 KB) |
| Storage for 100k vectors | ~307 MB (vectors only, before index overhead) |
| Storage for 1M vectors | ~3 GB |
| Similarity range on 15 mixed sentences | 0.29 to 0.75 (mean 0.45) |

For comparison, one chat answer from `qwen2.5:3b` took ~5 s. Embedding is about **50× cheaper** than
generation, which is why RAG embeds thousands of chunks and leaves only the final answer to the chat model.

## The questions to ask

| Question | Why it matters | Rule of thumb |
|---|---|---|
| **What language(s) is the content in?** | Many models are English-first; others are multilingual | Test on *your* language, not a benchmark |
| **How long are the texts?** | Every model has a max input (tokens); longer text is cut off silently | Chunk to fit (POC 3); check the model's max length |
| **How many dimensions?** | More dimensions can mean better quality, but more storage and slower search | 384–1024 is common; size storage = dims × 4 bytes × vectors |
| **Where must it run?** | Data privacy, cost, latency | Local (Ollama) = private and free; hosted API = no ops, costs per token, data leaves |
| **Does it need prefixes or instructions?** | nomic needs `search_query:` / `search_document:`; others need none or different ones | Read the model card; measured effect here: top-1 score 0.66 → 0.76 |
| **How good is it on *our* data?** | Public benchmarks don't know your domain | Build a small golden set (20 questions → expected documents) and measure hit@k (POC 5) |
| **Batch support?** | Batch embedding is much faster for ingestion | Here: 35% faster for 15 texts; more for bigger batches |

## The hidden cost: switching models later

Vectors from different models live in **different spaces**. A query embedded with model B cannot be compared
with documents embedded with model A. Switching models means:

1. re-embedding **every** document (time and cost);
2. building a new index, usually side by side, then cutting over;
3. re-checking any score thresholds, because each model has its own score range.

**Mitigations:**
- Store the **model name and version** with every vector (as metadata) so mixed indexes are detectable.
- Keep the original text, so re-embedding is always possible.
- Make the embedding model a config value (`EMBED_MODEL`), as this project does.

## Thresholds: don't hard-code 0.5

- Scores are relative to the model. Here even unrelated sentences score ~0.3, and related ones only ~0.7.
- Libraries differ: LangChain4j stores report (cosine + 1) / 2, so "0.5" there means cosine 0.
- **Prefer top-k ranking.** If you need a cut-off (e.g. to say "nothing relevant found"), calibrate it on a
  golden set for *this* model, and re-calibrate when the model changes.

## Recommendation for this project

**`nomic-embed-text` via Ollama:** local (no API key, data stays on the laptop), fast enough on CPU (~90 ms),
768 dimensions (~3 KB per chunk, trivial for ~25 notes), English content matches its strengths.
Use its task prefixes. Revisit only if retrieval quality in POC 5 is not good enough. That decision should be
based on hit@k measured on our golden set, not on benchmark tables.
