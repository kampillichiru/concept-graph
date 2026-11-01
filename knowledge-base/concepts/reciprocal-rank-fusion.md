---
id: reciprocal-rank-fusion
title: Reciprocal rank fusion (RRF)
category: Retrieval
level: 3
requires: [hybrid-search]
related: [bm25, reranking]
---
## What it is
Reciprocal rank fusion (RRF) combines several ranked lists into one. A document scores 1 / (k + rank) in each
list it appears in, and the scores are summed. A typical k is 60.

## How it works
Suppose a chunk is first in the keyword list and third in the vector list. Its RRF score is 1/61 + 1/63. A chunk
that is high in both lists beats one that is high in only one. Because only ranks are used, the different
score scales of BM25 and cosine do not matter.

## Pitfalls
RRF ignores how confident each list was: a first place with a score of 0.31 counts as much as one with 0.95.
When one retriever is clearly better for your questions, a weighted combination can beat plain RRF.
