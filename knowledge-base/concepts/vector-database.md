---
id: vector-database
title: Vector database
category: Retrieval
level: 2
requires: [embeddings, cosine-similarity]
related: [rag, chunking]
---
## What it is
A vector database stores embeddings with their metadata and answers nearest-neighbour queries: "give me the 5
stored vectors closest to this query vector". Qdrant is one example; pgvector and Neo4j's vector index are others.

## How it works
Each stored point has an id, a vector and a payload (a JSON object with fields such as conceptId and section).
Queries can combine vector search with payload filters, for example "only level 2 concepts". Upserting a point
with an existing id overwrites it, which makes repeated ingestion safe.

## Pitfalls
A vector database finds similar text, not correct text. It returns the top-k results even when none of them
is relevant, so the application must decide what to do with weak matches. Point ids must be UUIDs or integers.
