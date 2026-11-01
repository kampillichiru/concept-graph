---
id: rag
title: Retrieval-augmented generation (RAG)
category: Retrieval
level: 2
requires: [llm, embeddings, vector-database, chunking]
related: [grounding-and-citations, hallucination, hybrid-search]
---
## What it is
Retrieval-augmented generation answers a question in two steps: first retrieve the most relevant chunks from your own
documents, then ask the LLM to answer using only those chunks. The model does not need to know your data in advance.

## How it works
1. Embed the question with the same model used for the documents.
2. Search the vector database for the top-k chunks.
3. Put the chunks into the prompt, numbered or labelled with their source.
4. Ask the LLM to answer from the context and cite the sources.
The retrieved chunks are the evidence. Better retrieval gives better answers, and a bad retrieval cannot be fixed
by a better model.

## Pitfalls
RAG reduces hallucination but does not remove it. The model can ignore the context, mix in its training data, or
cite a chunk that does not say what it claims. If nothing relevant was retrieved, the honest answer is "I don't know".
