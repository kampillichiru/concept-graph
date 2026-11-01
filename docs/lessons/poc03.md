# POC 3: Vector database (Qdrant) and basic RAG

**Goal:** answer questions from *your* notes, with citations.

## 1. Why this POC exists

POC 1 showed that the model invents answers when it lacks knowledge. POC 2 showed how to find text by meaning.
POC 3 joins them: find the relevant notes, then have the model answer **only** from them, citing where each claim came from.

## 2. The pipeline

```
knowledge-base/concepts/*.md
   │  NoteLoader: split front matter (metadata) from body
   ▼
SectionChunker: one chunk per "## " section        (81 chunks from 27 notes)
   │  ConceptIngestor: embed "search_document: " + title + section
   ▼
Qdrant collection ai_concepts (768 dims, cosine)    point id = UUID from chunk id
   │
   ▼  question → embed "search_query: " + question → top-k nearest chunks
RagService: numbered prompt → chat model → answer with [concept-id] citations
   │
   ▼  code check: every citation must be a retrieved concept
```

## 3. What was built

```
tutor/src/main/
  java/com/conceptgraph/
    knowledge/ConceptNote.java         one note: front matter + body
    knowledge/NoteLoader.java          reads concepts/*.md, parses front matter (hand-written, ~40 lines)
    knowledge/Chunk.java               one section; stable id "embeddings#why-cosine"
    knowledge/SectionChunker.java      splits the body at "## " headings
    knowledge/ConceptIngestor.java     notes -> chunks -> vectors -> Qdrant (idempotent)
    knowledge/ChunkStrategy.java       SECTION (ai_concepts) or FIXED (ai_concepts_fixed), for the experiment
    knowledge/FixedSizeChunker.java    the baseline: 250-character windows, 50 overlap
    lessons/poc03/Poc03ChunkingController.java   /compare-chunking: both strategies, same questions
    retrieval/Embeddings.java          nomic prefixes in one place
    rag/RagService.java                RAG by hand: retrieve, build prompt, ask, check citations
    rag/NotesAssistant.java            AI Service for the LangChain4j version
    lessons/poc03/                     Poc03IngestController, Poc03SearchController,
                                       Poc03AskController, Poc03LangChainController
    llm/LlmConfig.java                 + QdrantClient, EmbeddingStore, NotesAssistant beans
  resources/prompts/rag-answer-v1.txt  prompt for the by-hand version
  resources/prompts/rag-system-v1.txt  system message for the LangChain4j version
knowledge-base/concepts/*.md           27 concept notes (drafted by Claude; review them)
```

### The core pieces, line by line

**Idempotent ids** (`ConceptIngestor.pointId`):
```java
UUID.nameUUIDFromBytes(chunkId.getBytes(UTF_8)).toString()
```
The same chunk id always gives the same UUID. Qdrant treats an upsert with an existing id as an overwrite,
so running ingest twice keeps the same point count (we saw 81 → 81).

**The grounding check** (`RagService.answer`):
```java
Set<String> retrieved = sources.stream().map(Source::conceptId).collect(toSet());
List<String> unknown = citations(answer).stream().filter(id -> !retrieved.contains(id)).distinct().toList();
```
The model can write any bracketed text. Code, not the prompt, decides whether a citation is valid.

**The prompt** (`prompts/rag-answer-v1.txt`): the instruction "Answer using ONLY the notes", the citation format,
and the exact refusal sentence. Every chunk is labelled with its concept id, so the model has something to cite.

## 4. How to run

1. Start Ollama (with `nomic-embed-text` and `qwen2.5:3b`) and Qdrant (`qdrant.exe`, port 6333/6334).
2. From the repo root: `mvn -q package -DskipTests` then `java -jar tutor/target/tutor.jar`.
   (Use `-Djava.version=21` with JDK 21.) The notes folder is found from the working directory; set
   `KNOWLEDGE_BASE_DIR` if you start from elsewhere.
3. Open http://localhost:8080/swagger-ui.html and go through **POC 3.1 → 3.5** in order.
4. Ingest: `POST /poc03/ingest`. Run it again: the point count in Qdrant stays the same.
   Check it with `curl http://localhost:6333/collections/ai_concepts`.

## 5. What to observe (measured on this laptop, CPU, qwen2.5:3b)

**Chunks** (`GET /poc03/chunks`): 81 chunks (27 notes × 3 sections). Length: min 141, median 243, max 405 characters.
Each chunk is short enough to read at a glance.

**Retrieval scores** (`GET /poc03/search`). Qdrant reports raw cosine here, not the (cos+1)/2 scale from POC 2:

| Question | Top 3 chunks | Scores |
|---|---|---|
| "Why do we split notes into chunks?" | chunking ×3 | 0.886, 0.876, 0.832 |
| "What is the capital of France?" | cosine-similarity, temperature, bm25 (Pitfalls) | 0.749, 0.746, 0.744 |

Relevant and irrelevant chunks are about 0.1 apart here. Those numbers come from one model and these notes; they do
not transfer to another setup.

**Same question, three ways** (`/ask-without-rag`, `/ask`, `/ask-augmented`), "Why do we split notes into chunks?":

| Path | Answer | Citations |
|---|---|---|
| without RAG | Talks about **musical** notes and rhythm chunks. Confident and wrong. | none |
| by hand (`/ask`) | "...ensure that each chunk is small enough to fit several in a prompt..." | `[chunking]` |
| LangChain4j (`/ask-augmented`) | Correct, shorter | `[chunking]` |

## 6. Break it

1. **Out of scope** ("What is the capital of France?"): both RAG paths answer "I don't know from my notes."
   Without RAG the model answers "Paris", which is correct, but nothing in the response shows where it came from.
2. **A bad citation.** With prompt v1, "What does MERGE do in Cypher?" produced `[MERGE]`, not `[cypher]`. The grounding
   check reported `unknownCitations: ["MERGE"]`. Prompt v2 (`rag-answer-v2.txt`) tells the model to copy the id exactly
   and to cite only ids from the notes. With v2 all 5 questions cite correctly, and the grounding check still runs
   on every answer, because the prompt alone is not a guarantee.
3. **The prefix difference.** `/ask` embeds the question with `search_query: `; `/ask-augmented` uses the
   LangChain4j retriever, which does not add it. Compare their sources for the same question.

### Chunking experiment (`GET /poc03/compare-chunking`)

Two collections, the same 8 labelled questions (`knowledge-base/evals/chunking-questions.txt`):
- **SECTION:** one chunk per `## ` heading (81 chunks, the tutor's collection `ai_concepts`).
- **FIXED:** 250-character windows with 50 overlap, ignoring headings (108 chunks, `ai_concepts_fixed`).

| Metric (k = 3) | SECTION | FIXED |
|---|---|---|
| hit@k: right concept in top-3 chunks | 8/8 | 8/8 |
| keyword@k: top-3 chunk contains the phrase the answer depends on | 8/8 | 8/8 |

**Read this honestly:** on these notes the two strategies tie. The notes are short (sections of 140–400 characters),
so a 250-character window seldom cuts the answer. The 8 questions are also too few to separate them, and I wrote
the notes in a section style, which favours SECTION. The difference shows up in the rows: FIXED chunks are
labelled `chars 200-450`, which a reader cannot cite; SECTION chunks are labelled `How it works`.

**How the measurement was corrected.** The first run showed 5/8 keyword hits for both strategies. Two of the misses
were my mistakes, not the chunking: the check compared raw text, so a phrase split by a line break in the Markdown
("does\nnot exist") did not match. One label was also wrong ("merging" where the note says "merges"). I fixed the
whitespace handling and the label, then re-ran. Both changes fix the measurement, not the outcome.

## 7. Done when

- [x] 5 test questions get correct citations (prompt v2): cosine-similarity, hallucination, temperature,
      hybrid-search, cypher. Prompt v1 got 4 of 5; the grounding check caught the one failure.
- [x] Re-running ingest creates no duplicates (81 points after two runs).
- [x] Fixed-size chunking compared with section chunking on the same questions (see above).
- [x] Architect artifact: [`docs/artifacts/poc03-chunking-strategy.md`](../artifacts/poc03-chunking-strategy.md).
