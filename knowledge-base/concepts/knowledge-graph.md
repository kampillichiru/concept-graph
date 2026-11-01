---
id: knowledge-graph
title: Knowledge graph
category: Graph
level: 3
requires: []
related: [cypher, graph-rag]
---
## What it is
A knowledge graph stores facts as nodes (things) and relationships (how they connect), for example
"GraphRAG REQUIRES RAG". Unlike a document store, the connections are first-class data you can query.

## How it works
Nodes and relationships can both carry properties. Typical questions are about paths: what must I learn before
this? Which concepts are connected to both A and B? The structure is explicit, so a query can compute the answer
exactly, without a model guessing.

## Pitfalls
A graph is only as good as its edges. Missing or wrong relationships give confidently wrong paths. Keep the
meaning of each relationship type strict (REQUIRES is not RELATED_TO) and check for cycles where order matters.
