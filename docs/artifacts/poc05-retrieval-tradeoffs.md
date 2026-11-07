# ADR 005: Retrieval method for the tutor (quality vs latency vs complexity)

**Status:** accepted for now, to be revisited when the golden set includes real user questions (POC 13).

## Context

The tutor answers from 81 section chunks (27 notes). POC 3 used vector search only. POC 5 built BM25, hybrid (RRF),
query rewriting and LLM reranking, and measured all of them on a 20-question golden set
(`knowledge-base/evals/golden-set.txt`, kinds: 7 exact, 10 paraphrase, 3 vague). Hardware: CPU-only laptop,
qwen2.5:3b for LLM steps, nomic-embed-text for vectors.

## The trade-off table (measured 2026-11-03)

| Method | hit@1 | hit@3 | MRR@10 | Median latency | LLM calls / query | Moving parts | Fails on |
|---|---|---|---|---|---|---|---|
| Vector | 15/20 | 16/20 | 0.799 | 106 ms | 0 (1 embed) | embed model + vector DB | paraphrases that share words with a *different* note; can't say "no match" |
| **BM25** | 16/20 | **20/20** | 0.883 | **2 ms** | 0 | ~100 lines of Java, in-memory index | synonyms, word forms ("merging" ≠ "merges") |
| Hybrid (plain RRF) | 15/20 | 16/20 | 0.806 | 115 ms | 0 (1 embed) | both of the above + fusion | a strong single-list winner loses to chunks found by both lists |
| Hybrid + LLM rerank | 16/20 | 20/20 | **0.892** | 20.6 s | 1 (≈545 tokens in) | + prompt, parser, model | latency; small model's ordering quality |
| Rewrite + hybrid | 12/20 | 19/20 | 0.750 | 1.5 s | 1 (≈100 tokens in) | + prompt | precise questions (adds off-topic words); model doesn't know domain terms |

Complexity scale used: each extra model in the path is one more thing to deploy, version, trace and evaluate.

## Decision

1. **First stage: BM25**, with hybrid available behind a flag. It's the best or tied on 4 of 5 quality numbers, the fastest
   by 50×, and it needs no model.
2. **No LLM reranker by default.** +0.009 MRR for +20 s is not worth it on a CPU. Revisit if a GPU or a small
   cross-encoder (tens of ms per pair) becomes available.
3. **No query rewriting on every query.** It lowered hit@1 from 15 to 12. Use it only on a retry path, after a grounding
   check fails (the "rewrite query" node in the POC 9 graph).
4. **Keep the eval as the gate.** Any change to retrieval must re-run `/poc05/eval` and report the table.

## Why not plain hybrid, the textbook answer?

Because it measured worse than one of its own inputs. Two causes, both visible in `/poc05/hybrid`:

- **RRF rewards agreement, not confidence.** A chunk that is first in BM25 but missing from vector's top 20 scores 1/61. Any chunk
  that appears in both lists, even at ranks 5 and 10, scores more. When one retriever is clearly better, plain RRF
  averages it down towards the weaker one.
- **We fuse chunks but score concepts.** Two retrievers can each rank a different chunk of the right note first; RRF
  counts them separately and splits the vote.

Next experiments, in order: concept-level fusion (sum votes per concept), then weighted RRF (bm25 1.0, vector 0.5). Both
must be judged on questions added *after* the weights are chosen, or the result is tuned to the test.

## Threats to validity (read before quoting the numbers)

- **The golden set favours BM25.** It was written by the notes' author: on average 62% of a "paraphrase" question's words
  appear in the expected note. Real learners' wording will favour vector search more.
- **Small set.** 20 questions → one question is 5 points of hit@k. A 1/20 difference is noise.
- **Small corpus.** BM25's idf is computed over 81 short chunks. With thousands of documents, both methods change.
- **Latency is retrieval only**, measured warm, on one laptop, sequentially.

## What would change the decision

| If… | Then… |
|---|---|
| Real user questions show vector winning on paraphrases | Make concept-level hybrid the default |
| A GPU or a cross-encoder reranker is available | Re-measure rerank; at < 200 ms it's likely worth it |
| The corpus grows past a few thousand chunks | Move BM25 into a search engine (Lucene / OpenSearch / Qdrant sparse vectors) instead of the in-memory index |
| Questions use internal codes or names the embedding model never saw | BM25 must stay in the path, whatever else is added |
