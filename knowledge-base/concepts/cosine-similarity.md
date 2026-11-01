---
id: cosine-similarity
title: Cosine similarity
category: Retrieval
level: 2
requires: [embeddings]
related: [vector-database]
---
## What it is
Cosine similarity measures how much two vectors point in the same direction, ignoring their length. The result is
1 for the same direction, 0 for unrelated, and -1 for opposite. It is the standard way to compare embeddings.

## How it works
The formula is the dot product of the two vectors divided by the product of their lengths. Normalised vectors
(length 1, as nomic produces) make it a plain dot product. Vector databases compute it for many stored vectors
and return the closest ones.

## Pitfalls
LangChain4j's in-memory store reports (cosine + 1) / 2, so 0.5 there means cosine 0. Always check which scale a
store reports before you set a threshold. Cosine also cannot tell you why two texts are similar.
