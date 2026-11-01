# POC 1: Talk to an LLM with LangChain4j (Spring Boot)

**Goal:** call a local model from a Spring Boot app and control its output.

## 1. The concepts (in plain words)

| Concept | What it means | Endpoint |
|---|---|---|
| **LLM** | A program that predicts the next piece of text, again and again. It doesn't "look things up"; it continues text in a plausible way. | all |
| **Token** | The unit an LLM reads and writes: roughly ¾ of a word. You pay for tokens (time on a laptop, money in the cloud). | `inputTokens` / `outputTokens` in every answer |
| **Context window** | The maximum number of tokens the model can see at once (prompt + history + answer). | memory lesson |
| **System vs user message** | *System* = standing instructions ("you are a concise tutor"). *User* = the actual question. Same question + different system message = different answer. | `GET /poc01/messages` |
| **Temperature** | How random the token choice is. 0 = always the most likely token (repeatable, factual). 0.8 = more variety (creative, less predictable). | `GET /poc01/temperature` |
| **Streaming** | Receive the answer token by token instead of all at once. Total time is the same; *time to first token* drops. | `GET /poc01/blocking`, `GET /poc01/stream` |
| **AI Service** | You write a Java **interface**; LangChain4j generates the implementation that builds the prompt, calls the model and converts the answer. Think Spring Data repositories, but for LLMs. | `ConceptExplainer` |
| **Structured output** | The model must answer in JSON that matches a schema generated from a Java record. You get a typed object, not text to parse. | `GET /poc01/concept-card` |
| **Chat memory** | The model is **stateless**. "Memory" is your app re-sending earlier messages on every call, stored **per conversation id** so users don't mix. | `POST /poc01/chat/{id}` |

## 2. What was built

```
pom.xml                                 parent = spring-boot-starter-parent 4.1.1, LangChain4j BOM 1.22.0
tutor/
  pom.xml                               spring-boot-starter-webmvc + langchain4j + langchain4j-ollama
  src/main/java/com/conceptgraph/
    TutorApplication.java               THE main class (@SpringBootApplication)
    config/ConceptGraphProperties.java  typed settings from application.yml / env vars
    llm/ModelFactory.java               the ONLY class that knows about Ollama
    llm/LlmConfig.java                  @Bean wiring: ChatModel, StreamingChatModel, AI Services, memory store
    llm/LoggingChatModelListener.java   logs model, tokens, latency of every LLM call (stderr)
    tutor/ConceptCard.java              record = the JSON schema for structured output
    tutor/ConceptExplainer.java         AI Service returning a ConceptCard
    tutor/TutorChat.java                AI Service with @MemoryId (memory per conversation)
    tutor/StatelessTutorChat.java       the same tutor without memory
    lessons/poc01/Poc01*Controller.java one controller per lesson
  src/main/resources/
    application.yml                     config; every value overridable by env var
    logback-spring.xml                  logs to stderr
    prompts/*-v1.txt                    versioned prompts
docs/lessons/poc01.http                 every request, ready to click
```

**How the pieces fit (Spring view):**

```
HTTP request ─▶ Poc01...Controller ─▶ ConceptExplainer / TutorChat   (interfaces, beans from LlmConfig)
                                          │  LangChain4j-generated implementation
                                          ▼
                                       ChatModel  (interface bean)  ◀── built by ModelFactory (Ollama)
                                          │  HTTP :11434
                                          ▼
                                       Ollama: qwen2.5:3b
```

**Key design choices:**
- Controllers and services depend only on the LangChain4j interfaces `ChatModel` / `StreamingChatModel`.
  Moving to a hosted model means changing `ModelFactory` and nothing else (architecture rule 5).
- Beans are wired **by hand** in `LlmConfig`. LangChain4j has a Spring Boot starter with `@AiService`
  that does it automatically; we skip it while learning so every piece is visible.

## 3. How to run

Ollama must be running (`ollama list` shows `qwen2.5:3b`). From the repo root:

```powershell
mvn -q package -DskipTests          # until JDK 25 is installed: add -Djava.version=21
java -jar tutor/target/tutor.jar    # or run TutorApplication from your IDE
```

Then open **Swagger UI: http://localhost:8080/swagger-ui.html**

- Endpoints are grouped by lesson (POC 1.1 … POC 1.5), each with a note on what to observe.
- Click an endpoint → **Try it out** → **Execute**. Default values are filled in.
- The **request duration** is shown under each response: that's the LLM latency.
- For the chat, type your message as the plain-text body, e.g. `What are embeddings?`.
- Exception: Swagger waits for the whole response, so it can't *show* streaming. Use `curl.exe -N` for `/stream`.

The raw OpenAPI spec is at http://localhost:8080/v3/api-docs. Alternatives: [`poc01.http`](poc01.http)
(IntelliJ / VS Code) or `curl.exe`:

```powershell
curl.exe http://localhost:8080/poc01/messages
curl.exe http://localhost:8080/poc01/temperature
curl.exe http://localhost:8080/poc01/made-up
curl.exe http://localhost:8080/poc01/blocking
curl.exe -N http://localhost:8080/poc01/stream                 # -N: print as it arrives
curl.exe "http://localhost:8080/poc01/concept-card?concept=embeddings"
curl.exe -X POST -H "Content-Type: text/plain" --data "What are embeddings?" http://localhost:8080/poc01/chat/alice
curl.exe -X POST -H "Content-Type: text/plain" --data "Give me an example of it." http://localhost:8080/poc01/chat/alice
curl.exe http://localhost:8080/poc01/chat/alice/memory
curl.exe "http://localhost:8080/poc01/model-comparison?models=qwen2.5:3b,qwen2.5:7b"   # several minutes
```

Settings (PowerShell: `$env:CHAT_MODEL="qwen2.5:7b"` before `java -jar`):

| Env var | Default | Purpose |
|---|---|---|
| `CHAT_MODEL` | `qwen2.5:3b` | which Ollama model |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | where Ollama runs |
| `CHAT_TIMEOUT` | `300s` | CPU models are slow |
| `LOG_REQUESTS` | `false` | `true` logs the raw JSON sent to / received from Ollama |
| `SERVER_PORT` | `8080` | HTTP port |

Watch the app's console: every LLM call logs a line like
`llm-trace : model=qwen2.5:3b inputTokens=87 outputTokens=63 latencyMs=8454`.

## 4. What to observe (results from real runs, qwen2.5:3b, CPU)

- **System message:** "concise tutor" gave a technical definition; "explain to a 10-year-old" gave an analogy. Same question.
- **Temperature:** at 0.8 the two runs differed in length and wording. Surprise: at 0.0 the two runs
  *also* differed slightly. Temperature 0 means "most likely token", but small numeric effects on CPU
  (e.g. prompt caching) can still change the result. Never assume perfect determinism.
- **Speed:** ~6–10 output tokens/s. The first call after start is slower (model loading: cold start).
- **Streaming:** blocking: first word after **~8 s**. Streaming: first token after **0.2–1.6 s**, same
  total (~7.5 s). That's why every chat UI streams.
- **Structured output:** `/concept-card` returns `{"title":"Embeddings Explained","oneLiner":...,"keyTerms":[...]}`.
  The LLM's JSON became a Java record, then Spring turned the record back into JSON.
- **Memory:**
  - without memory, "Give me an example of *it*" got an example of a *healthy snack*;
  - with memory (`alice`) it gave word embeddings ("cat" close to "kitty");
  - `bob` asking the same got nothing useful: **separate conversation id, separate memory**;
  - the trace showed input tokens **35 → 87**: memory is paid for in tokens on every call.

## 5. Break it

1. **Made-up framework** (`/poc01/made-up`): asked about the "QuantumBeanFactory in the Zorblax framework",
   the model wrote ~330 tokens of confident steps and Java code. Nothing exists. **This is why RAG (POC 3) exists.**
2. **The empty-JSON bug (we hit it for real):** the first `ConceptCard` had no `@JsonProperty(required = true)`.
   The schema said `"required": []`, so `{ }` was valid, and the model returned exactly that: 3 tokens, all
   fields `null`. The schema is a contract; a weak contract gets a lazy answer. Try it: remove the annotations,
   rebuild, run with `LOG_REQUESTS=true`.
3. `/poc01/concept-card?concept=Zorblax framework`: with "say so if you don't know" in the prompt, the model
   admitted it didn't know *and still invented an analogy*. Prompts reduce hallucination; they don't remove it.
4. **Shared memory bug:** in `LlmConfig`, replace `chatMemoryProvider(...)` with a single
   `.chatMemory(MessageWindowChatMemory.withMaxMessages(10))` and remove `@MemoryId` from `TutorChat`.
   Now chat as `alice`, then as `bob`. Bob sees Alice's conversation. That's a data leak in a real app.
5. Your turn: set `CHAT_TIMEOUT=2s` and `CHAT_MODEL=qwen2.5:7b`. What does the HTTP client get back?
   Where would you turn that into a clean error response (`@ControllerAdvice`)?

## 6. Done when

- [x] A `ConceptCard` for "embeddings" parses into a Java record.
- [x] A follow-up question uses memory.
- [x] Architect artifact: [`docs/artifacts/poc01-model-comparison.md`](../artifacts/poc01-model-comparison.md).

## 7. Architect artifact

`GET /poc01/model-comparison` compares `qwen2.5:3b` and `qwen2.5:7b`: cold start, latency, tokens/s, and the
3 answers side by side. First run: 3b averaged 5.2 s (8.8 tokens/s), 7b 11.5 s (4.1 tokens/s); 7b's cold start
was 18 s vs 2 s. Score the quality column yourself and write the decision. The habit: **measure, then decide**,
and write down why.
