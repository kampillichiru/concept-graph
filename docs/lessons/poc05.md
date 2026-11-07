# POC 5: Advanced RAG

**Goal:** make retrieval measurably better, and find out which tricks actually help on *these* notes.

## 1. Why this POC exists

POC 3 retrieves with vector search only. POC 4 showed that the LLM call is about 96% of the time, so it is cheap to
spend more effort on retrieval. But every "advanced RAG" trick has a price: more code, more latency, sometimes an extra
LLM call. This POC builds the standard tricks, then measures them on a golden set instead of trusting blog posts.

## 2. The concepts (in plain words)

| Concept | What it means | Where you see it |
|---|---|---|
| **BM25** | Keyword scoring: rare words count more, repeats saturate, long chunks are not rewarded for length | `/poc05/bm25`, `termScores` |
| **idf** | Inverse document frequency: how rare a word is across all chunks. Rare = high weight | `/poc05/bm25/terms` |
| **Hybrid search** | Run keyword and vector search on the same question, merge the lists | `/poc05/hybrid` |
| **RRF** | Reciprocal rank fusion: merge lists by rank, `1 / (60 + rank)` per list, summed | `hybrid[].ranks` |
| **Metadata filtering** | Restrict search to chunks whose payload matches (here: `category`), before ranking | `category=Graph` |
| **Query rewriting** | An LLM turns a vague question into search terms; we add them to the question | `/poc05/rewrite` |
| **Reranking** | A slower, smarter second pass reorders the top candidates | `/poc05/rerank` |
| **hit@k** | Was the expected concept in the top k results? | `/poc05/eval` |
| **MRR** | Mean reciprocal rank: 1 for rank 1, 0.5 for rank 2, 0.33 for rank 3, 0 if missed; averaged | `/poc05/eval` |

## 3. What was built

```
knowledge-base/evals/golden-set.txt   20 questions: question | expected concept | kind (exact, paraphrase, vague)
tutor/src/main/java/com/conceptgraph/
  retrieval/
    Bm25Index.java             BM25 written by hand (pure Java, no Spring)
    KeywordRetriever.java      BM25 over the same 81 section chunks as Qdrant; built in memory on first use
    VectorRetriever.java       the POC 3 vector search, plus a Qdrant payload filter
    ReciprocalRankFusion.java  RRF, returning each chunk's rank in each input list
    HybridRetriever.java       vector top 20 + BM25 top 20, fused with RRF
    QueryRewriter.java         LLM: question -> keywords (prompt query-rewrite-v2)
    LlmReranker.java           LLM: one listwise call reorders the top 8 (prompt rerank-v1)
    RetrievedChunk.java        the one result shape every retriever returns
  evals/
    GoldenSet.java             reads the golden set
    RetrievalEval.java         every method x every question; hit@1, hit@3, MRR@10, latency
  lessons/poc05/
    Poc05Bm25Controller.java     5.1  /poc05/bm25, /poc05/bm25/terms, POST /poc05/bm25/rebuild
    Poc05HybridController.java   5.2  /poc05/hybrid (with category filter)
    Poc05RewriteController.java  5.3  /poc05/rewrite
    Poc05RerankController.java   5.4  /poc05/rerank
    Poc05EvalController.java     5.5  /poc05/eval
  resources/prompts/query-rewrite-v1.txt, query-rewrite-v2.txt, rerank-v1.txt
```

New spans (POC 4 tracing): `retrieve.vector`, `retrieve.bm25`, `retrieve.hybrid`, `query.rewrite`, `rerank`, and
one `eval.case` trace per (question, method), so a bad eval row can be opened in `/poc04/traces/{id}`.

### Core piece 1: BM25 (`Bm25Index`)

Built once from the chunks:

```java
List<String> tokens = tokenize(indexText(chunk));       // "RRF combines..." -> [rrf, combines, ...]; stopwords dropped
tf.merge(t, 1, Integer::sum);                            // term frequency: how often each word is in THIS chunk
lengths[i] = tokens.size();                              // chunk length, for the length correction
tf.keySet().forEach(t -> docFreq.merge(t, 1, ...));      // document frequency: how many chunks contain the word
avgLength = total / chunks.size();                       // the "normal" length (30 terms here)
```

Scoring one chunk for one query term:

```java
double norm = K1 * (1 - B + B * lengths[i] / avgLength); // longer than average -> bigger norm -> lower score
idf(term) * tf * (K1 + 1) / (tf + norm)                  // rarity x saturating frequency
```

- **idf** = `log(1 + (N - n + 0.5) / (n + 0.5))`. Measured: `rrf` is in 4 of 81 chunks (idf 2.90), `model` is in 42 (idf 0.66).
  One rare word beats several common ones.
- **Saturation (k1 = 1.2):** with tf = 1 the fraction is 1.0; with tf = 5 it is about 1.8, not 5. Repeating a word does not buy rank.
- **Length (b = 0.75):** a chunk twice the average length needs more matches to score the same.
- **No stemming:** "merges" does not match "merge". That's a deliberate gap; see the exercises.

### Core piece 2: RRF (`ReciprocalRankFusion`)

```java
int rank = i + 1;                                        // position in this list, from 1
scores.merge(c.chunkId(), 1.0 / (k + rank), Double::sum);// each list votes 1/(60+rank); votes add up
ranks...put(listName, rank);                             // kept so you can see WHY a chunk won
```

Measured for "What is RRF?": `reciprocal-rank-fusion#what-it-is` is 1st in vector and 2nd in BM25, so it scores
1/61 + 1/62 = 0.03252, top of the fused list. The raw scores (cosine 0.88, BM25 4.15) are never added. They aren't on
the same scale.

### Core piece 3: the reranker prompt (listwise)

```
Rank the passages by how well they answer the question.
Question: ...
Passages:
[1] reranking: ## What it is Reranking is a second pass ...
[2] vector-database: ## Pitfalls A vector database finds ...
List the passage numbers from most to least relevant, separated by commas.
Reply with the numbers only, for example: 3,1,2
```

One call for 8 candidates (about 545 input tokens, 14 output tokens). A pointwise reranker would make 8 calls. The reply
is parsed in code (`parseOrder`): out-of-range or repeated numbers are ignored, and forgotten positions are appended, so a
garbage reply degrades to "hybrid order", never to an error.

## 4. How to run

1. Start Ollama, Qdrant and the tutor app as in POC 3. The `ai_concepts` collection must exist (`POST /poc03/ingest`).
2. Swagger: `http://localhost:8080/swagger-ui.html` → **POC 5.1 … 5.5**.
3. Suggested order:
   - `GET /poc05/bm25?query=What is RRF?` then `/poc05/bm25/terms?query=What is the model RRF?`
   - `GET /poc05/hybrid?query=What is RRF?` and `?query=Why does the model sometimes make things up?`
   - `GET /poc05/hybrid?query=How do I query paths?&category=Graph` (metadata filter)
   - `GET /poc05/rewrite` (default: "that graph thing with LLMs")
   - `GET /poc05/rerank` (default: "the second pass that sorts search results again")
   - `GET /poc05/eval?methods=VECTOR,BM25,HYBRID` (seconds), then the default (adds HYBRID_RERANK, about 7 minutes on CPU)
4. Edited a note? `POST /poc05/bm25/rebuild` (milliseconds) and `POST /poc03/ingest` (re-embeds).

## 5. What to observe (measured 2026-11-03, CPU laptop, qwen2.5:3b, nomic-embed-text, 81 chunks)

### The comparison table (`GET /poc05/eval`, all 5 methods)

| Method | hit@1 | hit@3 | MRR@10 | median ms | max ms | hit@3: exact / paraphrase / vague |
|---|---|---|---|---|---|---|
| VECTOR | 15/20 | 16/20 | 0.799 | 106 | 123 | 7/7, 7/10, 2/3 |
| **BM25** | **16/20** | **20/20** | 0.883 | **2** | 4 | 7/7, 10/10, 3/3 |
| HYBRID (RRF) | 15/20 | 16/20 | 0.806 | 115 | 138 | 7/7, 7/10, 2/3 |
| HYBRID + rerank | 16/20 | 20/20 | **0.892** | 20,566 | 22,151 | 7/7, 10/10, 3/3 |
| rewrite + HYBRID | 12/20 | 19/20 | 0.750 | 1,512 | 2,456 | 7/7, 9/10, 3/3 |

Latency is retrieval only (no answer generation). VECTOR's 106 ms is almost all the embedding call.

### Reading the table

**1. BM25 wins on these notes, and that's partly the golden set's fault.** The "paraphrase" questions were written
by someone who had read the notes. Measured: on average **62% of a paraphrase question's words also appear in the
expected note** ("How do I get the **same answer every time**…" vs the temperature note's "the same prompt gives nearly the
same answer"). Real learners use their own words. This is the most common eval mistake: the golden set's author knows the
documents. It isn't fixed by editing these questions (that would be tuning the test). It's fixed by adding questions
from real users (POC 13 logs them).

**2. Plain RRF made vector search no better, and hurt BM25's wins.** Three questions BM25 got at rank 1 fell to rank 5–7
in hybrid. The trace for "How do I get JSON back from the model instead of free text?":

```
structured-output#what-it-is    bm25 rank 1, vector rank >20   -> 1/61         = 0.0164
text-to-cypher#how-it-works     bm25 rank 4, vector rank 1     -> 1/64 + 1/61  = 0.0320  (fused #1, wrong)
```

A chunk in both lists always beats a chunk that is first in only one: two small votes beat one big vote. That is RRF
working as designed. The RRF note's own pitfall says so: *"When one retriever is clearly better for your questions, a
weighted combination can beat plain RRF."* A second effect: RRF fuses **chunks**, but we score **concepts**. For "Why does the
model make things up?", vector ranked `hallucination#how-it-works` 1st and BM25 ranked `hallucination#what-it-is` 1st.
Different chunks, so each got one vote, and `llm#how-it-works` (2nd + 5th) won.

**3. The reranker repairs RRF, at about 200× the latency of vector search.** Rerank fixed every hybrid miss, because the right chunk
was always in hybrid's top 8. It reached the best MRR (0.892), but at about 20 s per question on CPU. That's barely better
than BM25 at 2 ms. Its raw replies were real orderings (e.g. `1,6,8,3,4,2,7`), not lazy `1,2,3,...`.

**4. Rewriting helped vague questions a little and hurt precise ones.** hit@1 dropped from 15 to 12. "What are the k1 and b
parameters?" was expanded with `k1 k2 b cosine similarity`, which pulled in the cosine note: rank 1 fell to 3. On "that graph thing with LLMs",
`graph-rag` moved from rank 4 to 3. The 3B model often doesn't know the domain words ("GNN", "reinforcement learning" for
the agent loop). Rewriting pays only when you can tell vague questions from precise ones, and that decision belongs in a
router (POC 9), not on every query.

**5. Exact terms did not break vector search here** (7/7). The acronyms are in the note titles, and the title is embedded
with every chunk (POC 3 decision). See the break-it exercise for a case that does break it.

### Which method would I ship, and why

**For this tutor today: BM25 for the first stage, with hybrid behind a flag. No reranker by default.** BM25 is the best or
tied on every metric except MRR (0.883 vs 0.892, one question's difference), it's 50× faster than vector and 10,000× faster than
the reranker, and it needs no model. **But don't drop vector search:** the golden set is biased towards shared words, so real
paraphrases would favour vector. The change to try first is **weighted RRF or concept-level fusion**, measured on a golden set
that includes real users' questions. With 20 questions, one question = 5 points of hit@k, so differences of 1/20 are noise.

The full trade-off table is the architect artifact: [`docs/artifacts/poc05-retrieval-tradeoffs.md`](../artifacts/poc05-retrieval-tradeoffs.md).

## 6. Break it

**Prompt leak in the rewriter (measured).** `GET /poc05/rewrite?promptVersion=query-rewrite-v1`. Prompt v1 listed the notes'
topics ("LLMs, embeddings, RAG, vector databases, …") to help the model. For "that graph thing with LLMs" the model replied
`LLMs embeddings RAG vector-db`, copying the list, and results got *worse*: the top 3 became
`vector-database`, `rag`, `embeddings`. For "What is RRF?" it replied `RRF Not Found`. Prompt v2 replaces the list with
one worked example (few-shot). That gives `LLM graph LLMs GNN`, and `graph-rag` enters the top 3. Lesson: a small model copies
whatever looks like an answer in the prompt.

**Exact-term query on vector search.** The roadmap's break-it, and on these notes it *doesn't* break. Acronyms in titles
survive (see 5 above), and even `What is ReAct?` (the word appears once, in the body of `ai-agent`) finds `ai-agent` first
(cosine 0.83). The failure that does show up is a term that's in *no* note: `What is HNSW?`. Measured: BM25 returns **an empty
list** (an honest "no match"), while `GET /poc03/search?question=What is HNSW?` returns 3 chunks
(`tokens-and-context-window`, `hallucination`, `rag`) at 0.74, which looks like a normal score. Vector search can't say "I
don't know". To make vector search miss an exact term, use one the embedding model has never seen: an error code or
an internal product name in a new note.

**No stemming.** `GET /poc05/bm25?query=merging` vs `?query=merges`. Measured: `merging` returns nothing; `merges` returns both `hybrid-search` chunks. Add a
3-line suffix stripper to `Bm25Index.tokenize` (drop a trailing "s", "es", "ing") and re-run the eval. Did it help or hurt?

**Weighted RRF (exercise).** Give `ReciprocalRankFusion.fuse` a weight per list (`weight / (k + rank)`), try bm25 = 1.0 and
vector = 0.5, and re-run `/poc05/eval?methods=BM25,HYBRID`. Then ask yourself: did you just tune on your test set?

**Add real paraphrases.** Add 5 questions to `golden-set.txt` that share *no* words with their note (check with
`/poc05/bm25/terms`). Re-run the eval without a restart; the file is read on every call. Does BM25 still win?

## 7. Done when

- [x] BM25 written in Java, with the score explained per term.
- [x] Hybrid retriever with RRF; metadata filter on category (Qdrant payload filter + the same filter in BM25).
- [x] Query rewriter (LLM), with a measured prompt failure and fix (v1 → v2).
- [x] LLM reranker (listwise, one call), with a parser that can't fail.
- [x] 20-question golden set and the comparison table: vector vs BM25 vs hybrid vs hybrid+rerank (+ rewrite).
- [x] A decision: which method to ship and why (section 5).
- [x] Architect artifact: [`docs/artifacts/poc05-retrieval-tradeoffs.md`](../artifacts/poc05-retrieval-tradeoffs.md).
