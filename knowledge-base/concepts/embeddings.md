---
id: embeddings
title: Embeddings
category: Retrieval
level: 2
requires: [llm]
related: [cosine-similarity, vector-database, keyword-search]
---
## What it is
An embedding is a list of numbers (a vector) that represents the meaning of a piece of text. Texts with similar
meaning get vectors that point in similar directions, even when they share no words. An embedding model such as
nomic-embed-text produces 768 numbers per text.

## How it works
The embedding model is a small neural network that reads the text and outputs its vector. It does not generate
text. Search works by embedding the question with the same model, then finding the stored vectors closest to it.
Vectors from different models live in different spaces and cannot be compared.

## Pitfalls
Scores are relative: a similarity of 0.5 means nothing on its own. Models that expect task prefixes (nomic uses
"search_query: " and "search_document: ") give worse results when the prefixes are missing. Changing the model
means re-embedding every chunk.
