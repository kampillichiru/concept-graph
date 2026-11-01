---
id: reranking
title: Reranking
category: Retrieval
level: 3
requires: [hybrid-search, rag]
related: [reciprocal-rank-fusion]
---
## What it is
Reranking is a second pass over the top candidates from the first search. A more precise but slower model reads
the question together with each candidate and sorts them again. The first search is fast and broad; the reranker
is slow and accurate, and it only sees a few dozen candidates.

## How it works
The reranker can be a cross-encoder model, or an LLM asked to score relevance. The pipeline is: retrieve 20 chunks
cheaply, rerank them, keep the best 3 for the prompt. This is often the biggest quality gain per line of code.

## Pitfalls
An LLM reranker costs one model call per candidate, which is slow on a CPU laptop. Measure the quality gain against
the latency before you adopt it.
