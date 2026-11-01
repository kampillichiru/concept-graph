---
id: chunking
title: Chunking
category: Retrieval
level: 2
requires: [embeddings, vector-database]
related: [rag, tokens-and-context-window]
---
## What it is
Chunking is splitting documents into pieces before embedding them. Retrieval returns chunks, not whole documents,
so chunks should be small enough to fit several in a prompt and focused enough that one chunk answers one question.

## How it works
Two common strategies. Fixed-size chunks cut text every N characters, with some overlap; they are simple but can
split a sentence or a definition in half. Structural chunks follow the document's own sections (headings); each
chunk keeps a meaningful name, which is also a good citation label.

## Pitfalls
Chunks that are too small lose context ("it" no longer refers to anything). Chunks that are too large dilute the
embedding, so the vector represents several topics at once and matches none well. Measure the choice on real
questions instead of guessing.
