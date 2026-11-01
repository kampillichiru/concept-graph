# POC 2: Embeddings and similarity

**Goal:** understand meaning as numbers, the foundation of RAG.

## 1. Why this POC exists

POC 1 showed that an LLM invents answers when it lacks knowledge. The fix (RAG, POC 3) is:
*find the relevant notes, then let the LLM answer from them*. Finding the relevant notes by **meaning**,
not by matching words, is what embeddings make possible.

## 2. The concepts (in plain words)

| Concept | What it means | Endpoint |
|---|---|---|
| **Embedding** | A list of numbers (a **vector**) that represents the meaning of a text. `nomic-embed-text` gives **768** numbers per text. | `GET /poc02/embed` |
| **Embedding model** | A model that *converts* text to a vector. It doesn't generate text. Small and fast (~90 ms per sentence on your CPU vs seconds for a chat answer). | all |
| **Cosine similarity** | How much two vectors point in the same direction: 1 = same, 0 = unrelated. Similar meaning → similar direction. | `GET /poc02/similarity` |
| **Semantic vs keyword search** | Keyword search matches *words*. Semantic search matches *meaning* (nearest vectors). | `GET /poc02/search` |
| **Vector store / nearest neighbour** | Stores vectors and returns the *k* closest to a query vector. Here: LangChain4j's `InMemoryEmbeddingStore` (Qdrant in POC 3). | `GET /poc02/search` |
| **Task prefixes** | `nomic-embed-text` was trained with `search_query: ` on questions and `search_document: ` on documents. Using them changes the scores. | `GET /poc02/prefixes` |

## 3. What was built

```
tutor/src/main/
  java/com/conceptgraph/
    config/ConceptGraphProperties.java   + embedModel (EMBED_MODEL, default nomic-embed-text)
    llm/ModelFactory.java                + embeddingModel() -> OllamaEmbeddingModel behind the EmbeddingModel interface
    llm/LlmConfig.java                   + EmbeddingModel bean
    retrieval/VectorMath.java            cosine similarity and vector length, written by hand
    lessons/poc02/
      SentenceIndex.java                 loads the test sentences, embeds them once (lazily), two in-memory stores
      Poc02EmbeddingController.java      /embed, /similarity, /pairs
      Poc02MatrixController.java         /matrix
      Poc02SearchController.java         /search, /prefixes
      Poc02StatsController.java          /embedding-stats (artifact numbers)
  resources/poc02/sentences.txt          15 test sentences, chosen in groups
```

### The core piece: cosine similarity, line by line (`VectorMath.cosine`)

```java
double dot = 0, normA = 0, normB = 0;
for (int i = 0; i < a.length; i++) {
    dot   += a[i] * b[i];   // big when both vectors are big in the same positions
    normA += a[i] * a[i];   // squared length of a
    normB += b[i] * b[i];   // squared length of b
}
return dot / (Math.sqrt(normA) * Math.sqrt(normB));   // divide out the lengths: only direction is left
```

`/similarity` shows the hand-written result next to LangChain4j's `CosineSimilarity.between(...)`. They match (0.6759).

### How search works (`Poc02SearchController.semantic`)

```
query text ──embed──▶ query vector ──▶ store.search(k nearest) ──▶ sentences + scores
```

The sentences were embedded **once** and stored. Only the query is embedded at search time. That's why
vector search stays fast with thousands of documents.

## 4. How to run

Run `TutorApplication` from Eclipse (or `java -jar tutor/target/tutor.jar`), then open
**http://localhost:8080/swagger-ui.html** and use the **POC 2.x** groups. Ollama must have `nomic-embed-text`
(`ollama list`). The first POC 2 call embeds the 15 sentences (about 1 s); later calls reuse them.

For a guided run in learning order, use [`poc02.http`](poc02.http) (IntelliJ HTTP Client or VS Code REST Client).
Each request has an "Observe" and a "Takeaway" line.

## 5. What to observe (real results, nomic-embed-text, CPU)

**An embedding** (`/embed`): 768 numbers like `0.0132, 0.0512, -0.1549, …`, vector length **1.0**
(nomic normalises its vectors), **87 ms**. The numbers mean nothing on their own; only comparisons between them mean something.

**Hand-picked pairs** (`/pairs`):

| Cosine | Pair | Lesson |
|---|---|---|
| **0.745** | "Java" language vs "Java" island | **Surprise:** the *highest* score. A shared word still pulls vectors together |
| 0.739 | embeddings vs vector database | Same topic, different words: high |
| 0.735 | Java island vs Bali island | Both about islands: high |
| 0.707 | Java language vs Spring Boot | Related tech: high |
| 0.676 | AI agent vs autonomous assistant | Synonyms, no shared words: fairly high |
| 0.522 | "java" drink vs coffee beans | Same meaning, but "java" as coffee is rare, so the model links it weakly |
| 0.428 | AI agent vs pasta | Unrelated: lowest… but nowhere near 0 |

Embeddings capture meaning **much better than keywords, but not perfectly**. Small models still react
to surface words, and rare word senses ("java" = coffee) are weak.

**The matrix** (`/matrix`): score range across all pairs: **min 0.29, mean 0.45, max 0.75**. Even
"vector database" vs "pasta recipe" scores 0.29. **This is why a threshold of 0.5 means nothing on its own:**
here, 0.5 is just above average. Another model might put unrelated texts at 0.7. Scores only mean something
**compared with other scores from the same model**. Use the **ranking**, or calibrate a threshold per model with real data.

**Semantic vs keyword** (`/search`):

| Query | Semantic top 3 | Keyword |
|---|---|---|
| `coffee` | S15 coffee beans, **S6 "cup of java"**, S5 | S15 only: misses "java" |
| `software that works autonomously` | **S2 autonomous assistant**, S1 AI agent | **nothing** ("autonomously" ≠ "autonomous") |
| `holiday destinations` | S13 Bali, S5 Java island | S13 only |
| `how do I find similar text?` | S8 vector database, S9 RAG, S7 embeddings | S7 only (shares "text") |

Keyword search fails on synonyms and word forms. Semantic search finds the meaning.

**LangChain4j's score vs cosine:** vector stores return a *relevance score* = (cosine + 1) / 2. So a store
score of **0.5 means cosine 0**. `/search` shows both. Always check which scale a library uses before you set a threshold.

## 6. Break it

1. **Prefixes** (`/prefixes`): `What is a programming language?`

   | | #1 | #2 | #3 |
   |---|---|---|---|
   | with prefixes | S3 Java language **0.762** | S1 AI agent 0.599 | S5 Java island 0.589 |
   | without | S3 Java language 0.658 | **S5 Java island** 0.512 | S1 AI agent 0.491 |

   With prefixes, the right answer scores higher and the misleading "Java island" drops a place. The effect
   is modest here, but real: **how** you embed changes **what** you find. Using query/document prefixes with
   the wrong model, or forgetting them with the right model, silently lowers quality. Every embedding model
   has rules like this; read its model card.
2. **Mix models:** embed the documents with one model and the query with another (e.g. set `EMBED_MODEL`
   to a different model for queries only). The scores become meaningless: vectors from different models live
   in different "spaces". This is why switching embedding model means **re-embedding everything**.
3. **Your turn:** add a sentence to `sentences.txt` that shares many words with S1 but means something else
   (e.g. *"The travel agent uses tools to book hotels."*). Where does it rank for `What is an AI agent?`?

## 7. Done when

- [x] You can explain why a threshold of 0.5 means nothing on its own: scores are relative to the model,
      the range here is 0.29–0.75, and stores may even use a different scale.
- [x] Architect artifact: [`docs/artifacts/poc02-choosing-embedding-model.md`](../artifacts/poc02-choosing-embedding-model.md).
