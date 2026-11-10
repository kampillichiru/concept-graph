# POC 6: Knowledge graph in Neo4j (embedded, no Docker)

**Goal:** model how concepts connect, and answer "what do I need before this?" with a query, not with the LLM.

## 1. Why this POC exists

Vector search (POC 3, 5) finds text that *sounds like* the question. It cannot say that GraphRAG needs RAG, which needs
embeddings, which needs an LLM, or in what order to learn them. That structure is already in the notes' front matter
(`requires: [rag, knowledge-graph, embeddings]`). This POC puts it into a graph database and asks it questions.

The rule from `CLAUDE.md` applies: **the graph decides the structure, the LLM only explains it.** So every answer in this
POC comes from Cypher, and no model is called.

## 2. The concepts (in plain words)

| Concept | What it means | Where you see it |
|---|---|---|
| **Property graph** | Nodes and relationships, both with properties. `(:Concept {id, title})-[:REQUIRES]->(:Concept)` | `/poc06/stats` |
| **Cypher** | The query language. `MATCH` describes a pattern, `RETURN` says what to give back | the `cypher` field in every answer |
| **MERGE** | "Create this if it doesn't exist." Running the same load twice gives the same graph | `POST /poc06/load` twice |
| **Variable-length path** | `-[:REQUIRES*1..]->`: follow the relationship one or more times | `/poc06/path` |
| **Topological order** | An order where every concept comes after its prerequisites | `learningOrder` |
| **Cycle** | A path that leads back to its start. No learning order exists | `dryRun=true` with a cycle, section 5 |
| **DETACH DELETE** | Remove a node and its relationships | the reload step in `GraphLoader` |
| **Graph vs relational** | A relational schema answers one hop with one JOIN; the full path needs a recursive query | `/poc06/prerequisites` vs `/poc06/path` |

## 3. What was built

```
graph-server/                          a separate Maven module (not a child of Spring Boot)
  pom.xml                              Neo4j 5.26 harness + shade (one runnable jar)
  src/main/java/.../GraphServer.java   starts Neo4j in this JVM: Bolt on 7687, HTTP on 7474
tutor/src/main/java/com/conceptgraph/
  knowledge/NoteLoader.java            now also reads alternative_to and part_of
  graph/
    ConceptGraphModel.java             front matter -> nodes and edges, validated in plain Java
    CycleDetector.java                 depth-first search for a cycle in REQUIRES
    GraphLoader.java                   one write transaction: delete, then MERGE everything
    GraphQueries.java                  every read as Cypher (the constants are the queries)
    MermaidRenderer.java               learning path -> Mermaid flowchart text
    GraphConfig.java                   the Neo4j driver bean (Bolt client)
    GraphErrorHandler.java             server down -> 503 with the start command; invalid notes -> 422
  lessons/poc06/
    Poc06LoadController.java           6.1  POST /poc06/load, GET /poc06/stats
    Poc06PathController.java           6.2  GET /poc06/path, GET /poc06/prerequisites
    Poc06ExploreController.java        6.3  GET /poc06/neighbours, /no-prerequisites, /most-connected
    Poc06MermaidController.java        6.4  GET /poc06/path/mermaid
tutor/src/test/.../                    15 unit tests: cycles, validation, front matter, the real notes
```

### Core piece 1: the model checks before anything is written (`ConceptGraphModel`)

```java
for (String target : targets) {
    if (!byId.containsKey(target)) problems.add(note.id() + " requires unknown concept '" + target + "'");
}
...
CycleDetector.findCycle(requires).ifPresent(cycle -> { throw new IllegalArgumentException("cycle in REQUIRES: " ...); });
```

Every problem is collected and reported together, so one run shows all the broken notes. The loader runs this *before*
the write transaction, so a bad note leaves the old graph untouched.

Edges have one more rule. `RELATED_TO` and `ALTERNATIVE_TO` are symmetric: `rag` lists `hybrid-search` and
`hybrid-search` lists `rag`, and that is **one** relationship, not two. It is stored from the smaller id to the larger.
That is why the neighbours endpoint shows them as `both`: the stored direction carries no meaning.

### Core piece 2: the cycle check (`CycleDetector`)

Depth-first search with three colours. WHITE is unvisited, GRAY is on the current path, BLACK is finished.
Reaching a GRAY node means we walked back into our own path, and the part of the path from that node to here is the cycle:

```
graph-rag -> rag -> graph-rag
```

### Core piece 3: the learning path in Cypher (`GraphQueries.LEARNING_PATH`)

```cypher
MATCH path = (t:Concept {id: $id})-[:REQUIRES*1..]->(p:Concept)
WITH p, max(length(path)) AS depth
RETURN p.id AS id, p.title AS title, depth
ORDER BY depth DESC, id
```

- `REQUIRES*1..` follows requires-chains of any length.
- `max(length(path))` takes the **longest** chain from the target to each prerequisite. That is the key line.
  If `rag` needs `chunking`, and `chunking` needs `embeddings`, then `embeddings` sits deeper than `chunking`, which is
  deeper than `rag`. Sorting by depth descending gives an order where every prerequisite comes first.
- A concept needed by two others is placed before both, because it gets the depth of its *longest* route.

Measured on the real notes (7 steps for `graph-rag`):

```
llm(6) -> embeddings(5) -> cosine-similarity(4) -> vector-database(3) -> chunking(2) -> knowledge-graph(1) -> rag(1)
```

Check by hand: the longest chain to `llm` is graph-rag → rag → chunking → vector-database → cosine-similarity →
embeddings → llm, which is 6 relationships.

## 4. How to run

1. **Start the graph server first**, from the repo root, in its own terminal:
   ```
   java -jar graph-server/target/graph-server.jar
   ```
   You should see `bolt: bolt://127.0.0.1:7687/`. Leave it running. Stop it with Ctrl+C.
2. **Build** (once): `mvn -q package -DskipTests` from the repo root.
3. **Start the tutor**: `java -jar tutor/target/tutor.jar`.
4. **Load the graph**: `POST /poc06/load` (Swagger: **POC 6.1**). The graph is empty after every server start, so do
   this each time. It takes a few seconds the first time.
5. Then try: `GET /poc06/path?conceptId=graph-rag` (6.2), `GET /poc06/path?conceptId=langgraph`, `GET /poc06/neighbours?conceptId=rag` (6.3),
   `GET /poc06/path/mermaid?conceptId=graph-rag` (6.4, paste into https://mermaid.live).

No Ollama or Qdrant is needed for POC 6. No LLM is called.

## 5. What to observe

### The answer to "What before LangGraph?", by Cypher alone

```
GET /poc06/path?conceptId=langgraph
llm -> system-vs-user-messages -> structured-output -> tool-calling -> ai-agent
```

The `cypher` field in the response is the exact query that produced it. No model was involved.

### Path vs direct prerequisites

`/poc06/prerequisites?conceptId=langgraph` returns **one** concept: `ai-agent`. The full path returns five. The
difference is the transitive closure, and that is the step that needs recursion in SQL.

### Hubs and roots

- `/poc06/no-prerequisites` returns `keyword-search`, `knowledge-graph`, `llm`: the foundations.
- `/poc06/most-connected?limit=3` returns `rag` (14 relationships), `llm` (13), `embeddings` (10).
  Those are the concepts every path runs through.

### Break it: add a cycle (what the roadmap asks for)

Two outcomes, and both are worth seeing.

**a) The check in the loader (the defence).** Edit `knowledge-base/concepts/rag.md` and add `graph-rag` to its `requires`
(graph-rag already requires rag). Then:

```
POST /poc06/load?dryRun=true
422: cycle in REQUIRES: graph-rag -> rag -> graph-rag. A concept cannot come before itself, so no learning order exists.
```

Nothing is written. A real `POST /poc06/load` gives the same 422, and the graph keeps its previous contents. Revert the
edit when you are done.

**b) What happens if the cycle does get into the database.** I injected it straight into Neo4j with a Cypher statement
(bypassing the check), then asked for the path:

```
graph-rag path, 8 rows instead of 7:
  llm 6, embeddings 5, cosine-similarity 4, knowledge-graph 3, vector-database 3, chunking 2,
  graph-rag 2   <-- the target lists itself as its own prerequisite
  rag 1
```

Three things to take from it:
- **It does not crash.** Neo4j never reuses a relationship inside one path, so the search still stops.
- **It gives a wrong answer silently.** `graph-rag` appears in its own prerequisites, and `knowledge-graph` moved from
  depth 1 to depth 3.
- **So the check belongs in the loader**, before the data is written. A database will store anything you give it.

A reload (`POST /poc06/load`) removes the injected edge, because the load rebuilds the graph.

### Load twice

`POST /poc06/load` twice gives the same counts both times. Then `GET /poc06/stats`:
`Concept: 27, Category: 6, REQUIRES: 40, RELATED_TO: 37, ALTERNATIVE_TO: 1, IN_CATEGORY: 27`.

## 6. Decisions and why

- **Delete-then-MERGE in one transaction.** Plain MERGE never removes an edge, so a note that drops a `requires` would
  leave a stale edge behind forever. Rebuilding in one transaction avoids that. If the transaction fails, it rolls back.
- **Validation in Java before the write, not in Cypher.** The error message can name the exact loop and the exact
  unknown id. Cypher would only fail or store the bad data.
- **One query per edge type, with the type in the text.** Cypher cannot take a relationship type as a parameter. The
  type comes from our enum, never from user input, so this is safe. Model-generated Cypher (POC 7) must never do this.
- **`graph-server` is a separate module and not a Spring Boot child.** Neo4j brings its own Jersey, Jetty and Netty
  versions. Spring Boot's dependency management replaced them and startup failed (section 7).
- **Auth off, bound to localhost.** One user, one laptop. A shared server needs a password.

## 7. Known limits (be honest about these)

- **The graph is empty after every server start.** The store is rebuilt from the notes by `POST /poc06/load`.
  Persisting it across restarts did not work: the harness gives each start a fresh database folder, and my attempts to
  pin the folder hit a file-watcher error in Neo4j's startup. It is a known open item. Because the notes are the source
  of truth, a reload is the right fix for now.
- **Neo4j runs through `neo4j-harness`.** That is Neo4j's own embedding API, which is normally used for tests. It is
  the only public API we found that starts Bolt and HTTP in one JVM. The architecture doc says `org.neo4j:neo4j`;
  the harness is a thin layer over it.
- **Two Neo4j settings were needed to start on Java 25 and Spring-free dependencies.**
  - `enable_local_connector=false`: Neo4j's in-JVM Bolt channel fails on this JDK. We do not need it.
  - Jersey 2.46 + `jakarta.ws.rs-api` 2.1.6, pinned in the module: Neo4j's web stack. Spring Boot's versions broke it.
- **Harmless warnings at startup**: `sun.misc.Unsafe` (Neo4j internals) and a log4j `RollingRandomAccessFile`
  class-not-found message. Neo4j's own log files are not written, so Neo4j errors appear on the console instead.
- **Cypher enumerates all paths.** `REQUIRES*1..` counts every route, so a deep, densely connected target would get slow
  as the graph grows. With 27 notes the path query takes about 20 ms warm. This is the scaling reason to consider a
  memoised or Java-side topological sort later.
- **`PART_OF` exists in the schema but has no data.** No note uses it yet.

## 8. Done when

- [x] "What before LangGraph?" is answered by Cypher alone, no LLM (section 5).
- [x] A cycle is refused by the loader, and the message names the loop (section 5, break-it a).
- [x] Load is idempotent: two loads, the same counts.
- [x] Unit tests: 15 passing (`mvn -pl tutor test`).

The architect artifact for this POC is
[`docs/artifacts/poc06-graph-vs-vector-vs-sql.md`](../artifacts/poc06-graph-vs-vector-vs-sql.md).
The step-by-step test plan is [`docs/testing/poc06-testing.md`](../testing/poc06-testing.md).
