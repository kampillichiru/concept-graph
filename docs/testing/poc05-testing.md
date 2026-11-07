# POC 5 testing plan: advanced RAG, step by step

This plan tests POC 5 by hand: run each endpoint, compare what comes back with the expected result, and
understand **why** each check matters. The lesson, with the concepts and the decision, is
[`docs/lessons/poc05.md`](../lessons/poc05.md).

Expected values are from the run on 2026-11-03 (CPU laptop, qwen2.5:3b, nomic-embed-text, 81 chunks).
Rows marked **(predicted)** were not run yet: they say what the code should do, so running them is part of the test.

**How to read this plan.** Each section has three parts:
- **Why**: what could go wrong, and why this test catches it.
- **Steps / expected**: what to run and what you should see.
- **Explore**: open questions that teach more than a pass/fail.

Test in this order. Each layer depends on the one before: BM25 and vector are the inputs to hybrid, hybrid is the input
to rewrite and rerank, and the eval runs all of them. A bug found low down explains failures higher up.

```
0 setup -> 1 BM25 -> 2 hybrid + filter -> 3 rewrite -> 4 rerank -> 5 eval -> 6 tracing -> 7 break-it -> 8 edge cases
           (no model) (embed only)        (LLM)        (LLM)       (all)
```

---

## 0. Setup (once)

**Why:** most "bugs" in a RAG app are an empty collection or a stopped service. Rule these out first, so a failure later
means the code.

| Check | Command | Expected |
|---|---|---|
| Ollama has both models | `ollama list` | `nomic-embed-text`, `qwen2.5:3b` |
| Qdrant is up, collection exists | `curl http://localhost:6333/collections/ai_concepts` | `"status":"ok"`, `points_count` 81 |
| Java 25 for the build | `mvn -v` | `Java version: 25...` |
| Build | `mvn -q package -DskipTests` (repo root) | no output, `tutor/target/tutor.jar` updated |
| App from the repo root | `java -jar tutor/target/tutor.jar` | Swagger shows groups `POC 5.1` … `POC 5.5` |

No `points_count`? Run `POST /poc03/ingest` first. BM25 doesn't need Qdrant, but everything from section 2 on does.

---

## 1. BM25 keyword search (`POC 5.1`)

**Why:** BM25 is hand-written, so its maths is the most likely place for a bug. A wrong idf or length correction
doesn't crash anything. It just ranks badly, and you'd only notice it in the eval. These checks test the formula directly.

### 1a. One rare word wins
`GET /poc05/bm25?query=What is RRF?&k=3`

| Look at | Expected | What it proves |
|---|---|---|
| chunkIds | the 3 `reciprocal-rank-fusion#...` chunks | the title is indexed with every section |
| scores | 4.466, 4.147, 3.714 | ranking is deterministic: same input, same numbers |
| `termScores` | only `rrf` | `what` and `is` were removed as stopwords |

### 1b. Rarity (idf)
`GET /poc05/bm25/terms?query=What is the model RRF?`

| Field | Expected | Why it matters |
|---|---|---|
| `chunks` | 81 | the index sees the same chunks as Qdrant |
| `avgChunkLength` | 30.0 | used by the length correction |
| `rrf` | df 4, idf 2.903 | rare word, high weight |
| `model` | df 42, idf 0.657 | common word, low weight, but never negative |

**Hand check:** idf = ln(1 + (81 − 4 + 0.5) / (4 + 0.5)) = ln(18.22) = 2.903. If your calculator agrees, the formula is right.

### 1c. Rebuild
`POST /poc05/bm25/rebuild` → `{"chunks": 81}`.
**Why:** the index is cached in memory. This proves an edited note can be picked up without a restart.

**Explore:** `?query=RRF RRF RRF`: does the score triple? (It shouldn't; the query terms are de-duplicated.)
`?query=What is` (only stopwords) → **(predicted)** an empty list.

---

## 2. Hybrid search, RRF and the metadata filter (`POC 5.2`)

**Why:** fusion bugs are quiet. If the chunk ids from Qdrant and BM25 don't match, nothing fails: every chunk just
looks "found by one list only", and hybrid becomes a random mix. The `ranks` field makes this visible.

### 2a. Both lists agree
`GET /poc05/hybrid?query=What is RRF?&k=4`

| Look at | Expected |
|---|---|
| `hybrid[0]` | `reciprocal-rank-fusion#what-it-is`, rrfScore 0.03252, ranks `{vector: 1, bm25: 2}` |
| hand check | 1/61 + 1/62 = 0.03252 |
| `ranks` | most top chunks have **both** keys. If every entry has one key only, the ids don't match: bug |

### 2b. The lists disagree (the known RRF weakness)
`GET /poc05/hybrid?query=Why does the model sometimes make things up?&k=4`

| List | Expected top |
|---|---|
| vector | `hallucination#how-it-works` |
| bm25 | `hallucination#what-it-is` |
| hybrid | `llm#how-it-works` (ranks 2 and 5): hallucination drops out of the top 4 |

**Why this is a test, not a bug report:** it shows that RRF fuses chunks, not concepts. Two different chunks of the
right note each got one vote. If a later change (concept-level fusion) fixes this, this row is your regression test.

### 2c. Metadata filter
`GET /poc05/hybrid?query=How do I query paths?&k=3&category=Graph`

Expected: every chunk is from `cypher`, `graph-rag`, `knowledge-graph` or `text-to-cypher`.
**Why:** the filter runs in two places (Qdrant payload filter, Java predicate in BM25). Both lists must obey it, or
filtered results leak through the side that ignores it.

**Explore:** `category=graph` (lower case): BM25 ignores case, Qdrant doesn't. **(predicted)** vector returns nothing and
BM25 still returns results. Is that a bug? (Yes: the two sides should treat the filter the same way.)
`category=NoSuch` → **(predicted)** all three lists empty, no error.

---

## 3. Query rewriting (`POC 5.3`)

**Why:** the rewriter is an LLM call, so it can fail *quietly*: a plausible reply that makes retrieval worse. Compare
`before` and `after` every time. A rewrite that doesn't change the results only costs time.

`GET /poc05/rewrite?query=...`

| Query | Expected `keywords` (wording varies) | Expected effect |
|---|---|---|
| `that graph thing with LLMs` | `LLM graph LLMs GNN` | `graph-rag` enters the top 3 in `after` |
| `the second pass that sorts search results again` | `relevance feedback ranking` | `reranking` stays 1st, 2nd reranking chunk joins |
| `What is RRF?` | `RRF not a standard term…` | no gain: precise questions don't need a rewrite |

| Field | Expected |
|---|---|
| `rewriteMs` | 1–4 s warm (first call can be 12 s: the model is loading) |
| `searchMs` | about 100–150 ms |

**Regression test for the prompt fix:** `?promptVersion=query-rewrite-v1` with the graph question.
Expected: keywords like `LLMs embeddings RAG vector-db` (copied from the v1 prompt) and a **worse** `after`
(`vector-database`, `rag`, `embeddings`). If v2 ever starts doing this, the prompt regressed.

**Explore:** run the same query 3 times. Temperature is 0: are the keywords identical?

---

## 4. Reranking (`POC 5.4`)

**Why:** the reranker has two risks. (1) The model returns a lazy `1,2,3,...` and reranking silently does nothing.
(2) The model returns garbage, and the parser must still produce a full ordering, never an error.

`GET /poc05/rerank` (default: "the second pass that sorts search results again")

| Field | Expected |
|---|---|
| `rawReply` | a real order, e.g. `1,6,8,3,4,2,7`, **not** `1,2,3,4,5,6,7,8` |
| `rerankedOrder` | 8 ids, none missing or repeated, even if `rawReply` listed only 7 |
| `topK[0]` | `reranking#what-it-is` |
| `rerankMs` | about 20–25 s on CPU (about 545 input tokens) |
| `hybridMs` | about 200 ms |

**Why the "8 ids" check matters:** in the measured run the model listed 7 numbers, not 8. The parser appended
the missing one. That's the safety net working.

**Explore:** try `What is RRF?`. When hybrid is already right, does rerank keep the order? Is 20 s worth it?

---

## 5. The retrieval eval (`POC 5.5`)

**Why:** this is the test the POC exists for. Sections 1–4 check each part; the eval checks whether the parts
improve anything. Run it after **every** retrieval change. If the numbers move, you know why from sections 1–4.

**Fast run first** (seconds): `GET /poc05/eval?methods=VECTOR,BM25,HYBRID`
**Full run** (about 8 minutes): `GET /poc05/eval?methods=VECTOR,BM25,HYBRID,HYBRID_RERANK,REWRITE_HYBRID`

| Method | hit@1 | hit@3 | MRR | median ms |
|---|---|---|---|---|
| VECTOR | 15/20 | 16/20 | 0.799 | ~106 |
| BM25 | 16/20 | 20/20 | 0.883 | ~2 |
| HYBRID | 15/20 | 16/20 | 0.806 | ~115 |
| HYBRID_RERANK | 16/20 | 20/20 | 0.892 | ~20,500 |
| REWRITE_HYBRID | 12/20 | 19/20 | 0.750 | ~1,500 |

**Pass criteria:**
- VECTOR, BM25, HYBRID give **exactly** these numbers (no LLM in them, so they're deterministic). A change means
  the code, the notes, or the collection changed.
- RERANK and REWRITE may move by a question or two between runs (LLM output). More than that: investigate.

**Hand-check one row:** pick "How do I get the same answer every time I ask?" (VECTOR rank 9). Its reciprocal rank
is 1/9 = 0.111. Check it against `rows[].VECTOR.rank`. That one check proves the MRR maths.

**Explore:** add a question to `knowledge-base/evals/golden-set.txt` and re-run (no restart). See
the lesson for the "real paraphrases" exercise.

---

## 6. Tracing (POC 4 still works)

**Why:** POC 5 added LLM calls (rewrite, rerank). Architecture rule 3: every LLM call must be traceable.

| Step | Expected |
|---|---|
| Call `/poc05/rerank`, copy `traceId`, open `/poc04/traces/{traceId}` | tree: `poc05.rerank` → `retrieve.hybrid` (`retrieve.vector` with `embed`, `retrieve.bm25`) and `rerank` → `chat` |
| `rerank` span attributes | `rerank.reply`, `rerank.candidates` = 8, `rerank.prompt_version` |
| After a full eval, count `eval.case` lines in `traces/spans.jsonl` | 100 more (20 questions × 5 methods) |

---

## 7. Break-it experiments

Each one changes one thing, so you learn what each part is responsible for.

| Experiment | How | Expected (measured unless marked) | What you learn |
|---|---|---|---|
| Term in no note | `/poc05/bm25?query=What is HNSW?` vs `/poc03/search?question=What is HNSW?` | BM25 `[]`; vector 3 chunks at ~0.74 | Vector search can't say "no match" |
| Word forms | `/poc05/bm25?query=merging` vs `?query=merges` | `[]` vs 2 `hybrid-search` chunks | BM25 has no stemming |
| Prompt leak | rewrite with `promptVersion=query-rewrite-v1` | prompt's topic list comes back | Small models copy the prompt |
| Stop Ollama | stop it, call `/poc05/bm25` then `/poc05/hybrid` | **(predicted)** BM25 still works; hybrid 500 with an ERROR span | Which parts depend on a model |
| Stop Qdrant | stop it, call `/poc05/hybrid` | **(predicted)** 500; BM25 alone still works | Same, for the vector DB |
| Edit a note | add "HNSW" to a note, `POST /poc05/bm25/rebuild`, search again | **(predicted)** BM25 finds it; vector doesn't until `POST /poc03/ingest` | Two indexes, two refresh steps |
| Weighted RRF | see lesson exercise | your measurement | Tuning on the test set |

---

## 8. Edge cases (predicted, run them)

**Why:** these are the inputs a real user or the POC 9 agent will eventually send. Each should give a clear answer,
not a stack trace.

| Input | Predicted | Acceptable? |
|---|---|---|
| `/poc05/eval?methods=FOO` | HTTP 400 (Spring can't convert to the enum) | yes |
| `/poc05/rewrite?promptVersion=v9` | HTTP 500, "Unknown prompt version" in the log | no: should be 400. Fix candidate |
| `/poc05/bm25?query=` (empty) | `[]` | yes |
| `/poc05/hybrid?k=0` | **check it**: Qdrant may reject `maxResults=0` | decide: 400, or empty list |
| `category=graph` vs `Graph` | the two retrievers disagree (section 2) | no: make both case-insensitive, or both exact |
| Very long query (2,000 chars) to `/poc05/rerank` | works, slower (longer prompt) | yes, but cap the query length in POC 13 |

---

## 9. Automated tests to add (planned)

The plan above is manual. These parts are pure functions, so they're cheap to unit-test and worth it, because
they're the maths the eval relies on:

| Class | Test | Why |
|---|---|---|
| `Bm25Index` | idf of a term in 4 of 81 chunks = 2.903; stopword-only query → empty; tf saturates | the hand-written formula |
| `ReciprocalRankFusion` | two lists → expected order and score 1/61 + 1/62 | the fusion rule |
| `LlmReranker.parseOrder` | `"3,1"` with n=4 → `[3,1,2,4]`; `"9,0,x"` → `[1,2,3,4]`; repeats ignored | the parser that must never fail |
| `RetrievalEval.rankOf` / `reciprocal` | rank 0 → 0; rank 4 → 0.25 | the metric itself |
| `GoldenSet` | a line without 3 parts → clear error | bad eval data fails loudly |

None of these needs Ollama or Qdrant, so they can run in CI.

---

## 10. Record your results

| Date | Test | Expected | Actual | Notes |
|---|---|---|---|---|
| | 1b idf of `rrf` | 2.903 | | |
| | 2a RRF score | 0.03252 | | |
| | 4 rerankedOrder has 8 unique ids | yes | | |
| | 5 eval VECTOR/BM25/HYBRID | exactly as table | | |
| | 8 edge cases | as predicted | | |

LLM steps vary a little between runs. Judge the **ranks and ids**, not the exact keyword wording.
