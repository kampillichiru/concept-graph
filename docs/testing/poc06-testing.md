# POC 6 testing plan: the knowledge graph, step by step

This plan tests POC 6 by hand: run each step, compare with the expected result, and understand why the check matters.
The lesson with the concepts and the decisions is [`docs/lessons/poc06.md`](../lessons/poc06.md).

Expected values are from the run on 2026-11-07 (Windows laptop, JDK 25, Neo4j 5.26.33 via neo4j-harness).

```
0 setup -> 1 unit tests -> 2 graph-server -> 3 load -> 4 learning path -> 5 explore -> 6 mermaid -> 7 break it -> 8 failure modes
```

---

## 0. Setup (once)

| Check | Command | Expected |
|---|---|---|
| Java 25 | `java -version` | `25.x` |
| Build everything | `mvn -q package -DskipTests` (repo root) | no output; `graph-server/target/graph-server.jar` and `tutor/target/tutor.jar` exist |

No Ollama and no Qdrant are needed for this POC.

---

## 1. Unit tests (no database)

**Why:** cycle detection, validation and the front-matter parser are plain Java. Test them without starting anything.

Run: `mvn -pl tutor test`

| Test class | What it proves |
|---|---|
| `CycleDetectorTest` | a chain is acyclic, a 2-node loop and a self-loop are found, a diamond is not a cycle |
| `ConceptGraphModelTest` | unknown ids, duplicate ids and cycles are rejected; symmetric edges are stored once; `theRealKnowledgeBaseIsAValidGraph` passes on the real notes |
| `NoteLoaderFrontMatterTest` | `alternative_to` and `part_of` are read; missing keys are empty lists, not null |

**Expected:** `Tests run: 15, Failures: 0, Errors: 0`.

If `theRealKnowledgeBaseIsAValidGraph` fails, a note you edited broke the graph. Read the message: it names the note.

---

## 2. Start graph-server

**Why:** the tutor talks to Neo4j over Bolt. If the server is not up, the failure should be clear, not a stack trace.

Terminal 1, from the repo root: `java -jar graph-server/target/graph-server.jar`

| Look at | Expected |
|---|---|
| console | `bolt:  bolt://127.0.0.1:7687/` and `http:  http://localhost:7474/` |
| console | `Neo4j is running (embedded, no Docker)` |
| console | no `Unsafe` or native-access warning about `java -jar` (the manifest sets `Enable-Native-Access`) |
| `curl -s http://localhost:7474/` | JSON with `bolt_direct` |

Expected extra noise, harmless: `sun.misc.Unsafe` warnings and one log4j `RollingRandomAccessFile CLASS_NOT_FOUND` line.

---

## 3. Start the tutor and load the graph (6.1)

Terminal 2: `java -jar tutor/target/tutor.jar`, then Swagger at `http://localhost:8080/swagger-ui.html` → **POC 6.1**.

### 3a. Dry run
`POST /poc06/load?dryRun=true`

**Expected:** `{"dryRun":true,"concepts":27,"categories":6,"requires":40,"relatedTo":37,"alternativeTo":1,"partOf":0,...}`
and nothing written.

### 3b. Real load, twice
`POST /poc06/load` then `POST /poc06/load` again.

**Expected:** both return the same counts. The second one proves the load is idempotent.

### 3c. Stats
`GET /poc06/stats`

**Expected:** `nodesByLabel: {Category: 6, Concept: 27}`,
`relationshipsByType: {ALTERNATIVE_TO: 1, IN_CATEGORY: 27, RELATED_TO: 37, REQUIRES: 40}`.

**Explore:** `Concept` equals the number of files in `knowledge-base/concepts`. Why is `IN_CATEGORY` 27 and not something else?

---

## 4. Learning path (6.2)

### 4a. The required answer
`GET /poc06/path?conceptId=langgraph`

**Expected order:** `llm → system-vs-user-messages → structured-output → tool-calling → ai-agent`
(LangGraph is the target and is not listed).

### 4b. The default concept
`GET /poc06/path?conceptId=graph-rag`

**Expected:** 7 steps, depths 6,5,4,3,2,1,1:
`llm(6) → embeddings(5) → cosine-similarity(4) → vector-database(3) → chunking(2) → knowledge-graph(1) → rag(1)`.

**Check by hand:** the longest chain to `llm` is graph-rag → rag → chunking → vector-database → cosine-similarity →
embeddings → llm. That is 6 relationships.

### 4c. Each prerequisite comes before what needs it
Look at `learningOrder`. For every step, every concept it requires must appear **earlier**. For `rag`: `chunking`,
`vector-database`, `embeddings`, `llm` must all come before it.

### 4d. Direct vs transitive
`GET /poc06/prerequisites?conceptId=langgraph`

**Expected:** one concept, `ai-agent`. The full path has five. The difference is the recursive part.

### 4e. The Cypher is shown
Every answer has a `cypher` field. Copy it into the Neo4j Browser (`http://localhost:7474`) and run it with the
`$id` replaced by `'graph-rag'`. You should get the same rows.

---

## 5. Explore the graph (6.3)

### 5a. Neighbours
`GET /poc06/neighbours?conceptId=rag`

**Expected:** `REQUIRES out` to `chunking`, `embeddings`, `llm`, `vector-database`; `REQUIRES in` from `graph-rag`,
`grounding-and-citations`, `prompt-injection`, `reranking`; `RELATED_TO both` to five concepts; `ALTERNATIVE_TO both` to `graph-rag`.

**Why `both`:** RELATED_TO and ALTERNATIVE_TO are symmetric. They are stored once, from the smaller id, so showing `in` or
`out` would be meaningless.

### 5b. Roots
`GET /poc06/no-prerequisites` → `keyword-search`, `knowledge-graph`, `llm`.

### 5c. Hubs
`GET /poc06/most-connected?limit=5` → `rag` 14, `llm` 13, `embeddings` 10, `hybrid-search` 10, `vector-database` 8.

**Check:** `rag` has 5 RELATED_TO, 4 REQUIRES out, 4 REQUIRES in, 1 ALTERNATIVE_TO = 14.

---

## 6. Mermaid diagram (6.4)

`GET /poc06/path/mermaid?conceptId=graph-rag` (plain text).

**Expected:** starts with `flowchart LR`; the target node has `:::target`; arrows go from prerequisite to dependent
(`n1 --> n7` where `n1` is Embeddings and `n7` is GraphRAG).

Paste the output into https://mermaid.live to see the picture.

---

## 7. Break it: a cycle

**Why:** this is the roadmap's question, and it has two answers. The check must stop the first one.

### 7a. The loader refuses it (the defence)
1. Copy `knowledge-base/concepts` to a scratch folder. Edit the copy's `rag.md`: add `graph-rag` to `requires`.
2. Start a second tutor on another port, pointed at the copy. `KNOWLEDGE_BASE_DIR` is the folder that *contains*
   `concepts` (the loader adds `/concepts` itself), so copy the whole `knowledge-base` folder and set
   `KNOWLEDGE_BASE_DIR=<scratch>/knowledge-base` and `SERVER_PORT=8081`.
3. `POST http://localhost:8081/poc06/load?dryRun=true`

**Expected:** HTTP 422, `cycle in REQUIRES: graph-rag -> rag -> graph-rag. ...`. A real load on 8081 also gives 422,
and `GET /poc06/stats` on 8080 is unchanged (27 / 40).

Edit the scratch copy only. Never edit the repo's note to test this.

### 7b. What happens if the cycle gets into the database
Bypass the check: run a Cypher statement against the server's HTTP API that adds `rag -REQUIRES-> graph-rag` in the live
database. Then `GET /poc06/path?conceptId=graph-rag`.

**Expected (measured):** 8 rows instead of 7. `graph-rag` appears in its own prerequisites at depth 2, and
`knowledge-graph` moves from depth 1 to depth 3. No error.

Then run `POST /poc06/load` on 8080 to restore. Check that `graph-rag` returns 7 rows again.

---

## 8. Failure modes

| Test | How | Expected |
|---|---|---|
| graph-server down | stop it (Ctrl+C), then `GET /poc06/path?conceptId=graph-rag` | HTTP 503, message says to run `java -jar graph-server/target/graph-server.jar` |
| graph-server back | start it again, then the same call | works again with no tutor restart (the driver reconnects) |
| unknown concept | `GET /poc06/path?conceptId=nope` | HTTP 404, message says ids are the note file names |
| server restarted | stop and start graph-server, then `GET /poc06/stats` | `{}` empty. This is expected: the graph is not kept between starts. Run `POST /poc06/load` again. |

The last row is a known limit (see the lesson, section 7), not a bug to fix in this test.

---

## 9. Cleanup

- Stop the scratch tutor on 8081 if you started it.
- Revert any edit to `knowledge-base/` (`git status` must show no changes there).
- Leave graph-server running, or stop it with Ctrl+C.
