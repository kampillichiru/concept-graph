---
id: graph-rag
title: GraphRAG
category: Graph
level: 3
requires: [rag, knowledge-graph, embeddings]
related: [text-to-cypher, hybrid-search]
alternative_to: [rag]
---
## What it is
GraphRAG combines a knowledge graph with text retrieval. It finds the entities a question is about, expands
the graph a hop or two to collect connected facts, and gives those facts together with the text chunks to the LLM.
It is built for questions that need connections, not just similar text.

## How it works
1. Link the question to graph nodes (entity linking, often with vector search over node summaries).
2. Expand the neighbourhood with a short graph query, for example one or two hops of RELATED_TO.
3. Combine the graph facts with text chunks in the prompt.
4. Answer with citations to both facts and chunks.

## Pitfalls
GraphRAG is more work to build and to keep current: the graph must be maintained. For a question that a single
chunk answers, plain RAG is simpler and just as good. Use GraphRAG when measurements show multi-hop questions failing.
