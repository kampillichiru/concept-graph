# CLAUDE.md: ConceptGraph

A **learning project** (AI Concepts Tutor). The owner is a senior Java/microservices engineer
preparing for Forward Deployed Engineer / AI Solution Architect roles.
Read `docs/architecture.md` and `docs/roadmap.md` before changing anything.

## How to help
- Explain the *why* before writing code. Short explanations with a small example.
- Work one POC at a time (see `docs/roadmap.md`). Don't build ahead.
- Each POC: one REST controller per lesson in the Spring Boot `tutor` app (endpoints under
  `/pocNN/...`), a `docs/lessons/pocNN.md` page (concept, how to run,
  what to observe, break-it exercise), and one architect artifact.
- Generate boilerplate freely; explain core pieces (prompts, chunking, Cypher, graph nodes) line by line.

## Constraints (never violate)
- **No Docker.** Ollama and Qdrant run natively; Neo4j runs embedded in the `graph-server` module.
- **Java 25 LTS + Spring Boot 4** for the app: LangChain4j, LangGraph4j, MCP Java SDK, OpenTelemetry.
- **No model API keys.** Own agents use Ollama. Claude takes part only through Claude Code as an
  MCP client. Keep the provider configurable.
- CPU-only laptop: keep prompts short, avoid LLM calls where code can do the job.

## Architecture rules
1. Answers are grounded in retrieved notes or graph facts and cite `[concept-id]`.
2. The graph decides structure (prerequisites, paths); the LLM only explains it.
3. Every LLM call is traceable (LangSmith via OpenTelemetry, or a local trace file).
4. Model-generated Cypher is read-only, enforced by code. MCP tools are read-only.
5. Code depends on LangChain4j interfaces, never on a specific provider.

## Conventions
- Base package `com.conceptgraph.<area>`; lessons in `com.conceptgraph.lessons.pocNN.PocNN...Controller`.
- Each lesson controller gets a Swagger `@Tag("POC N.M ...")` and an `@Operation` per endpoint saying
  what to observe. Swagger UI: http://localhost:8080/swagger-ui.html (springdoc-openapi).
- Main class: `com.conceptgraph.TutorApplication`. LangChain4j beans are wired explicitly in
  `llm/LlmConfig` (no LangChain4j auto-config starter, so every piece stays visible).
- Prompts in `tutor/src/main/resources/prompts/<name>-v1.txt`, versioned.
- Config from env vars with defaults (`CHAT_MODEL`, `EMBED_MODEL`, `QDRANT_HOST`, `NEO4J_URI`,
  `LANGSMITH_API_KEY`, `LANGSMITH_PROJECT`).
- Logs go to stderr (stdout is reserved for MCP stdio in POC 11).
- Build: `mvn -q package -DskipTests` from the repo root; run: `java -jar tutor/target/tutor.jar`.
  (Until JDK 25 is installed: add `-Djava.version=21`.)
