# ConceptGraph: an AI Concepts Tutor in Java

A **learning project** on the path to *Forward Deployed Engineer* and *AI Solution Architect*.
It is built one small POC at a time, all in **Java 25 LTS + Spring Boot 4**, on a Windows laptop, **without Docker**.

## The use case in one paragraph

You ask questions about AI concepts, and the tutor answers from a curated knowledge base, shows you
how the concepts connect, gives you a learning path, and quizzes you. For example:

- *"What is RAG and why do we chunk documents?"*: answered from notes, with citations (**RAG + vector DB**)
- *"What should I learn before LangGraph?"*: walks the prerequisite graph (**graph DB**)
- *"How is GraphRAG different from normal RAG?"*: combines text and graph facts (**GraphRAG**)
- *"Quiz me on embeddings"*: a multi-step workflow that pauses for your answer (**LangGraph4j agent**)
- In **Claude Code**: *"Using my concept tutor, plan my week of study"*: Claude calls your Java tools (**MCP**)

The knowledge base *is* about AI concepts, so you learn every concept twice: once by reading the
notes, once by building the part of the system that serves them.

## What you will learn

| Area | Technology (Java) | Where |
|------|-------------------|-------|
| LLM calls, prompts, structured output, memory | **LangChain4j** + Ollama | POC 1 |
| Embeddings, similarity | LangChain4j + `nomic-embed-text` | POC 2 |
| Vector database, RAG | **Qdrant** (`qdrant.exe`) | POC 3 |
| Tracing and observability | **LangSmith** via OpenTelemetry | POC 4 |
| Hybrid search, query rewriting, reranking | BM25 in Java + RRF | POC 5 |
| Connected graph database | **Neo4j**, embedded in Java | POC 6 |
| GraphRAG, Text-to-Cypher | Neo4j + Qdrant + LLM | POC 7 |
| Your own agent with tools | LangChain4j AI Services + `@Tool` | POC 8 |
| Stateful agent workflows | **LangGraph4j** | POC 9 |
| Multi-agent (supervisor) | LangGraph4j | POC 10 |
| MCP server (used by Claude Code) | **MCP Java SDK** | POC 11 |
| MCP client (used by your agent) | LangChain4j MCP | POC 12 |
| Evals, guardrails, semantic cache | Java | POC 13 |
| Capstone: Tutor API + architecture pack | Java | POC 14 |

The full roadmap is in [docs/roadmap.md](docs/roadmap.md), and the system design is in
[docs/architecture.md](docs/architecture.md).

## Constraints this design respects

| Constraint | How we handle it |
|-----------|------------------|
| **No Docker** | Ollama and Qdrant run as native Windows programs. Neo4j runs *inside a Java process* (embedded Neo4j, a Maven dependency). |
| **Only a Claude Pro subscription, no API key** | Our own agents use local models through Ollama. Claude takes part through **Claude Code as an MCP client** of our Java server (POC 11). A Pro plan does not include an API key, but the code keeps the model provider configurable in case you get one later. |
| **CPU-only laptop, 16 GB RAM** | LoanPilot measured ~50–160 s per answer with `qwen2.5:7b`. Retrieval, graph and eval steps run without an LLM, so they're fast. We recommend `qwen2.5:3b` for quick iterations and `qwen2.5:7b` for quality checks. |
| **Java only** | LangChain4j (Java LangChain), LangGraph4j (Java LangGraph), OpenTelemetry for LangSmith (there is no LangSmith Java SDK). |

## Prerequisites

| Tool | Status | Needed from |
|------|--------|-------------|
| JDK 25 LTS | ⚠️ not installed yet (JDK 21 works meanwhile: add `-Djava.version=21` to mvn) | POC 1 |
| Maven 3.10 | ✅ installed | POC 1 |
| Ollama + `qwen2.5:7b` + `nomic-embed-text` | ✅ installed | POC 1 |
| `qwen2.5:3b` (default chat model) | ✅ installed | POC 1 |
| Qdrant (`E:\Chiru\AIStuff\qdrant\qdrant.exe`) | ✅ available | POC 3 |
| LangSmith account (free Developer plan, API key) | sign up at smith.langchain.com | POC 4 |
| Neo4j | nothing to install: embedded via Maven | POC 6 |
| Node.js (for MCP Inspector, optional) | check `node -v` | POC 11 |

## Run it

```powershell
mvn -q package -DskipTests          # add -Djava.version=21 until JDK 25 is installed
java -jar tutor/target/tutor.jar    # main class: com.conceptgraph.TutorApplication, port 8080
```

Then open **Swagger UI: http://localhost:8080/swagger-ui.html**. Each lesson is a group of REST
endpoints (`/poc01/...`): click **Try it out** → **Execute**. Alternatives:
[`docs/lessons/poc01.http`](docs/lessons/poc01.http) or `curl.exe`.

## How we work

1. One POC at a time. Read its section in [docs/roadmap.md](docs/roadmap.md) first.
2. Build it, run it, break it on purpose, and observe.
3. Write 5 lines in [LEARNINGS.md](LEARNINGS.md): this becomes interview and blog material.
4. Every POC also produces one **architect artifact** (ADR, trade-off table, demo script).
   That is the FDE / Solution Architect habit we're building.

## Status

| # | POC | Status |
|---|-----|--------|
| 0 | Design: use case, architecture, roadmap | ✅ done |
| 1 | Talk to an LLM with LangChain4j ([lesson](docs/lessons/poc01.md)) | ✅ done |
| 2 | Embeddings and similarity ([lesson](docs/lessons/poc02.md)) | ✅ done |
| 3 | Vector database (Qdrant) and basic RAG ([lesson](docs/lessons/poc03.md)) | ✅ done |
| 4 | Observability with LangSmith ([lesson](docs/lessons/poc04.md)) | ✅ done |
| 5 | Advanced RAG: BM25, hybrid, rewriting, reranking ([lesson](docs/lessons/poc05.md)) | ✅ done |
| 6–14 | See [docs/roadmap.md](docs/roadmap.md) | ⏳ not started |
