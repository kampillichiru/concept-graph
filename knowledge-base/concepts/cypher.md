---
id: cypher
title: Cypher
category: Graph
level: 3
requires: [knowledge-graph]
related: [text-to-cypher]
---
## What it is
Cypher is the query language of Neo4j. It describes graph patterns with ASCII art: (a:Concept)-[:REQUIRES]->(b:Concept)
means "a concept a that requires a concept b".

## How it works
MATCH finds patterns, WHERE filters them, RETURN outputs them. MERGE creates a node or relationship only if it does
not exist yet, which makes loads repeatable. Variable-length paths such as [:REQUIRES*] follow chains of any length.

## Pitfalls
Write clauses (CREATE, MERGE, DELETE, SET) change the database. Any query that comes from a user or a model must be
checked for them before it runs. Unbounded paths can be very slow on large graphs.
