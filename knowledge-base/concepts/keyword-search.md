---
id: keyword-search
title: Keyword search
category: Retrieval
level: 2
requires: []
related: [embeddings, bm25, hybrid-search]
---
## What it is
Keyword search finds documents that contain the words of the query. It is exact and fast, and it is the search
behind most "Ctrl+F"-style tools and classic search engines.

## How it works
The simplest version counts how many query words appear in each document. Better versions (BM25) weigh rare words
more than common ones and adjust for document length. Keyword search needs no model at all.

## Pitfalls
It fails on synonyms and word forms: "autonomously" does not match "autonomous". It is excellent for exact
identifiers such as error codes or function names, where semantic search can be vague. This is why hybrid
search combines both.
