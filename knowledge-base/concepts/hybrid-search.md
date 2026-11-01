---
id: hybrid-search
title: Hybrid search
category: Retrieval
level: 3
requires: [embeddings, bm25]
related: [reciprocal-rank-fusion, reranking]
---
## What it is
Hybrid search runs a keyword search and a vector search on the same question, then merges the two result lists.
It covers both failure modes: exact terms (keyword) and paraphrases (vector).

## How it works
Both searches return a ranked list. The lists have different score scales, so they cannot simply be added.
A rank-based fusion method such as reciprocal rank fusion merges them using positions only.

## Pitfalls
Hybrid search doubles the retrieval work and adds a tuning knob (how much each side counts). It is only worth the
complexity when measurements show that either search alone misses questions.
