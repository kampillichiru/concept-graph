# POC 4: Observability with LangSmith (OpenTelemetry)

**Goal:** see inside every LLM call and retrieval.

## 1. Why this POC exists

From POC 3 on, one answer involves several steps: embed the question, search Qdrant, call the chat model, check
the citations. When an answer is wrong or slow, you need to see which step did what, in what order, and how long it
took. Logs show lines; a trace shows the structure.

## 2. The concepts (in plain words)

| Concept | What it means | Where you see it |
|---|---|---|
| **Trace** | All the work caused by one request, with one trace id | `traceId` in `/poc04/ask` |
| **Span** | One timed step inside a trace: a name, a start, an end, attributes, a status | each line of the tree |
| **Parent / child** | A span started while another was active becomes its child. This makes the tree | the indentation in `/poc04/traces/{id}` |
| **Attributes** | Key/value facts on a span: the model, token counts, hit count | `attributes` in the response |
| **Status** | `UNSET`, `OK` or `ERROR`. An error span also records the exception | `<-- ERROR` in the tree |
| **GenAI conventions** | Standard attribute names for LLM calls: `gen_ai.request.model`, `gen_ai.usage.input_tokens`, ... | the `chat` and `embed` spans |
| **OpenTelemetry (OTel)** | The standard API and SDK for spans, and a wire format (OTLP) that many tools accept | the `tracing` package |

## 3. What was built

```
tutor/src/main/java/com/conceptgraph/
  tracing/
    TracingConfig.java                 builds the OTel SDK: 3 exporters (below)
    Traces.java                        inSpan(name, block): the span is current while the block runs
    TracingChatModelListener.java      one span per LLM call (LangChain4j hook, POC 4.1)
    TracingEmbeddingModelListener.java one span per embedding call
    RecentSpanStore.java               exporter 1: keeps the last 1000 spans in memory
    JsonlSpanExporter.java             exporter 2: appends one JSON line per span to traces/spans.jsonl
                                       (exporter 3, OTLP to LangSmith, is added only when LANGSMITH_API_KEY is set)
  rag/RagService.java                  spans: rag.answer, rag.retrieve, qdrant.search
  llm/ModelFactory.java                adds the tracing listeners to the chat and embedding models
  lessons/poc04/
    Poc04AskController.java            /poc04/ask: root span poc04.ask, returns the traceId
    Poc04TraceController.java          /poc04/traces, /poc04/traces/{traceId}: reads the spans back
```

### The core piece: a span around a block (`Traces.inSpan`)

```java
Span span = tracer.spanBuilder(name).startSpan();          // starts the timer
try (Scope ignored = span.makeCurrent()) {                 // this span is now the parent of anything inside
    return body.apply(span);                               // the work
} catch (RuntimeException e) {
    span.recordException(e);                               // keep the error on the span
    span.setStatus(StatusCode.ERROR, ...);
    throw e;                                               // the caller still sees the error
} finally {
    span.end();                                            // stops the timer, hands the span to the exporters
}
```

`makeCurrent()` is what creates the tree. The chat listener does not get a parent passed in; it starts its span
from "the current span", which is `rag.answer` at that moment.

### How the LLM span is started and ended

LangChain4j calls a listener before and after each model call. The span is started in `onRequest`, stored in the
request context, and ended in `onResponse` (or `onError`). Token counts come from the response, so they are the
numbers the provider reported, not estimates.

## 4. How to run

1. Start Ollama, Qdrant, and the tutor app, as in POC 3.
2. Open `http://localhost:8080/swagger-ui.html` and go to **POC 4.1**.
3. `GET /poc04/ask?question=What does cosine similarity measure?` Copy the `traceId`.
4. `GET /poc04/traces/{traceId}` and read the tree.
5. The same spans are in `traces/spans.jsonl` (repo root). Each line is one span.

**Port 8080 busy?** If another app already listens on 8080, the tutor will not start. Run it on another port with
`SERVER_PORT=8081`, then use `http://localhost:8081`.

## 5. What to observe (measured on the project laptop, CPU, qwen2.5:3b)

Question: "What does cosine similarity measure?"

```
poc04.ask  (27949 ms)
  rag.answer  (27943 ms)
    rag.retrieve  (1184 ms)
      embed nomic-embed-text  (905 ms)     768 dimensions, 50 characters in
      qdrant.search  (254 ms)              top-3 from ai_concepts
    chat qwen2.5:3b  (26747 ms)            346 input tokens, 26 output tokens, finish STOP
```

**Where did the time go?** The chat call: about 96% of the time. Retrieval took about 4%. That is why the rest of the
architecture (POC 5 reranking, POC 13 semantic cache) targets the LLM call first.

**Tokens:** 346 in and 26 out. Most of the cost is the input (the prompt with the notes), not the answer.

## 6. Break it

**Missing model.** Restart the app with `CHAT_MODEL=no-such-model:1b` (not stopping Ollama). The question
returns HTTP 500, and the tree shows the failure:

```
poc04.ask  (1988 ms)  <-- ERROR
  rag.answer  (1976 ms)  <-- ERROR
    rag.retrieve  (1836 ms)
      embed nomic-embed-text  (751 ms)
      qdrant.search  (1038 ms)
    chat no-such-model:1b  (120 ms)  <-- ERROR
```

Reading it: retrieval worked, the chat call failed after 120 ms, and the error propagated to the root. Without a trace,
you would see only a 500 in the log.

**Stop Ollama mid-run** (the original break-it idea). Measured: a question was sent, then `ollama.exe` was killed about
5 s in, while the chat call was running. The response was HTTP 500 with no `traceId` in the body, so find the trace in
`traces/spans.jsonl` (the latest `poc04.ask` line). The tree:

```
poc04.ask  (6843 ms)  <-- ERROR
  rag.answer  (6842 ms)  <-- ERROR
    rag.retrieve  (123 ms)               rag.top_score 0.91, 3 hits
      embed nomic-embed-text  (105 ms)
      qdrant.search  (13 ms)
    chat qwen2.5:3b  (6714 ms)  <-- ERROR
```

Reading it: retrieval finished, and the chat call failed after about 6.7 s, when the connection dropped. The error
reached the root span. Note: the 500 response has no `traceId`, so the trace id is only in the span file or the log. A future improvement
is to return the id in the error body.

**Empty retrieval:** ask about something not in the notes. The `rag.retrieve` span still has `rag.hits` = 3, because
Qdrant always returns the nearest k. Retrieval itself has no notion of "relevant enough". That is the gap POC 13 covers
with a score threshold.

## 7. Send the traces to LangSmith (optional)

Without a key, everything stays on this laptop. To send the same spans to LangSmith:

1. Get an API key from LangSmith (a tracing key, not the model key you don't have).
2. Set environment variables before starting the app:
   ```
   LANGSMITH_API_KEY=<your key>
   LANGSMITH_PROJECT=conceptgraph
   ```
3. Start the app. Spans are sent in batches to `LANGSMITH_OTLP_ENDPOINT`
   (default `https://api.smith.langchain.com/otel/v1/traces`) with the `x-api-key` header. If your account is on
   the APAC host, set `LANGSMITH_OTLP_ENDPOINT=https://apac.api.smith.langchain.com/otel/v1/traces` as well. With the
   wrong region, every key gets a 403.
4. In LangSmith, open the project `conceptgraph`. Each question appears as one trace.

**Status (live test, 2026-11-02): done.** The trace reached LangSmith.

- **The region matters.** The account lives on the APAC host. With the US endpoint, every key was refused with
  `403`, including new keys from the same workspace. Setting `LANGSMITH_OTLP_ENDPOINT` to
  `https://apac.api.smith.langchain.com/otel/v1/traces` fixed it. The endpoint is the full export URL, including `/v1/traces`.
- **How I checked:** the app answered one question, and the `conceptgraph` project on the APAC host listed the
  run tree: `poc04.ask` → `rag.answer` → `rag.retrieve` (`embed`, `qdrant.search`) and `chat`, with no errors.
- **What the failure looked like before:** the question still returned 200 with the answer, and the trace was
  still in `/poc04/traces`. The only sign was a `WARN` in the log: `Failed to export spans ... HTTP status code 403`.
  Tracing never breaks the request, but nothing reaches LangSmith.

## 9. Learnings from the live run

- **Tracing is best-effort.** An exporter failure logs a warning and the answer still comes back. Good for the
  user, but it means a broken key looks like "it works" unless you check the log or the project.
- **Check the key against the API before blaming the code.** One REST call tells you whether the key is valid.
  `403` on both the REST API and the OTLP endpoint means the key, not the exporter.
- **Environment variables need a fresh process.** `setx` saves the key for new terminals only. The running app
  keeps what it was started with. Restart after changing a key.
- **Use `$env:` in PowerShell and `%VAR%` in cmd.** Mixing them gives "syntax is incorrect" errors.
- **Local tracing is the safety net.** `traces/spans.jsonl` and `/poc04/traces` still have the full tree while
  LangSmith is unreachable. That's the reason the file exporter is always on.

## 8. Done when

- [x] One RAG question appears as one trace with nested spans (`/poc04/ask` → `rag.answer` → `rag.retrieve`, `chat`).
- [x] Each LLM call records its model, token counts, and finish reason.
- [x] A failing call shows up as an ERROR span in the same tree.
- [x] The trace appears in LangSmith (APAC host, project `conceptgraph`).
- [x] Architect artifact: [`docs/artifacts/poc04-llm-monitoring.md`](../artifacts/poc04-llm-monitoring.md).
