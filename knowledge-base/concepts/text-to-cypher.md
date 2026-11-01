---
id: text-to-cypher
title: Text-to-Cypher
category: Graph
level: 3
requires: [cypher, knowledge-graph, llm]
related: [graph-rag]
---
## What it is
Text-to-Cypher is asking an LLM to write a Cypher query from a natural-language question. The query then runs
against the graph, and the result is the answer. It lets users ask structural questions without knowing Cypher.

## How it works
The prompt includes the graph schema (node labels, relationship types, property names) and the question. The model
returns a query, which is checked and executed. If the database returns an error, the error text can be sent back
to the model for one repair attempt.

## Pitfalls
The model can write a query that deletes data, or that is valid but answers a different question. Guardrails
must be code, not instructions: reject write clauses before execution, limit the result size, and use a read-only
database user where possible.
