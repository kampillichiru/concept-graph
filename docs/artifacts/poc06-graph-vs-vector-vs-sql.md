# Graph DB vs vector DB vs SQL: which one for which question

**Context:** the tutor answers three kinds of question. "What is X?" needs similar text (vector search, POC 3/5).
"What do I need before X?" needs structure (a graph, POC 6). "How many concepts have no prerequisites?" needs a plain
query over facts (SQL or Cypher). This one-pager says which store answers which, with evidence from POC 6.

## The one-line rule

> Use the store that matches the **shape of the question**: similarity → vector, connection → graph, aggregation over rows → SQL.

## Comparison

| Question shape | Example | Best fit | Why | Evidence from this project |
|---|---|---|---|---|
| Find text that *means* something similar | "How do I keep the model from making things up?" | **Vector DB** (+ BM25) | Embeddings capture meaning; no structure is known | Vector hit@3 16/20, BM25 20/20 (POC 5); see ADR 005 |
| Follow relationships of unknown length | "Everything I need before LangGraph, in order" | **Graph DB** | Variable-length paths and ordering are the native operation | `/poc06/path?conceptId=langgraph` answers in one Cypher query, 5 steps, ~20 ms warm |
| One hop, fixed shape | "What does graph-rag directly require?" | **SQL or Cypher** | A single JOIN or one match; no need for a graph | `/poc06/prerequisites`: 3 rows |
| Aggregate over rows | "How many concepts per category?" | **SQL** | Group-by is what SQL is built for | `/poc06/stats` is a count per label, trivially expressible in SQL |
| Recursive closure in SQL | Same as the path question, relational | SQL with a recursive CTE | Possible, but the ordering and cycle handling are manual | Not built; the recursive CTE is the equivalent we did not write |

## What the graph gave us that the others did not

1. **Ordering by longest chain** (`max(length(path))`) is one clause. The same ordering in SQL is a recursive CTE plus
   a window function, and it is easy to get wrong.
2. **Cycles are visible.** A cycle does not crash the query: it gives a wrong answer silently (measured: the target lists
   itself as a prerequisite). So the cycle check must run **before** the data is written, in the loader.
3. **Explainability.** Each answer ships with the query that made it. That is the traceability rule from the architecture.

## What the graph cost us

- **Another process.** Neo4j runs in `graph-server`; the tutor is a client over Bolt. One more thing to start.
- **Dependency friction.** Neo4j brings its own web stack. Spring Boot's dependency management broke startup until the
  module was separated. Expect this with any embedded server database.
- **Operational gap.** The store is rebuilt from the notes on every start. Persisting it safely is open work.
- **Path enumeration.** `REQUIRES*1..` enumerates every route. Fine for 27 concepts; a deep, dense graph would need a
  memoised or Java-side topological sort.

## Decision for this project

1. **Vector DB stays the answer to "what is X".** Graph facts are not text, so they do not replace retrieval.
2. **Graph DB answers the structure questions** (prerequisites, paths, neighbours). Cypher is generated only by code we
   wrote, never by the model, until POC 7 adds a read-only guard.
3. **SQL is not added.** No question so far needs it. The notes are the source of truth, and the graph is derived from them.
4. **Validate before write, always.** Any graph load runs the cycle and reference checks first.

## When I would change this

- The graph grows past a few thousand concepts, or users create concepts at runtime: revisit the store (and the path
  query) and add persistence, auth, and backups.
- Questions become mostly aggregates over many attributes: a relational store plus the graph as a derived index.
- The team already runs a managed graph service: use it instead of an embedded one. The loader and queries would not change.
