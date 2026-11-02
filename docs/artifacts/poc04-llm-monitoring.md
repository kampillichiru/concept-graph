# POC 4 artifact: what to monitor in production for an LLM app

One page for a customer conversation: *"How would we know the system is healthy in production?"*
The numbers come from the traces of POC 4 (CPU laptop, Ollama, qwen2.5:3b, 27 notes).

## What the traces showed

| Step | Typical time | Share of one question | Notes |
|---|---|---|---|
| Embed the question | ~0.9 s | 3% | first call may include model loading |
| Qdrant search | ~0.3 s | 1% | 81 chunks, small collection |
| Chat (LLM) | ~27 s | 96% | 346 input tokens, 26 output tokens |

**The LLM call dominates latency and cost.** Anything that reduces the LLM calls or their input size has the biggest effect.

## What to monitor

| Signal | Why | Measure | Alert when (starting point, tune later) |
|---|---|---|---|
| **Latency p95 per span** | users feel the slowest 5% | p50, p95, p99 for `chat`, `rag.retrieve`, `qdrant.search` | p95 of the whole request above the agreed SLO |
| **Input tokens per request** | cost and speed of the LLM call | `gen_ai.usage.input_tokens` | sudden rise (a prompt or a note got much bigger) |
| **Output tokens per request** | cost, and a sign of rambling answers | `gen_ai.usage.output_tokens` | sudden rise |
| **Error rate per span** | which dependency is failing | share of spans with status `ERROR`, grouped by span name | any sustained rise in `chat` or `embed` errors |
| **Empty or weak retrieval** | the model has nothing to answer from | `rag.top_score` below a threshold; `rag.hits` = 0 when a threshold is used | rise in weak retrievals (usually a missing note or a bad question) |
| **Citation failures** | answers that cite things that were not retrieved | `rag.unknown_citations` > 0 | any rise above the baseline (POC 3: 0 for the 5 test questions) |
| **Model and prompt version** | to explain a change in quality | `gen_ai.request.model`, `rag.prompt_version` | every deploy that changes either |

## What to avoid putting in traces

- **Full prompts and answers.** They contain the notes, and can contain user data. The traces here store counts and
  ids, not the text. Keep text only in a protected store, with a retention limit.
- **Secrets.** The LangSmith key goes in an environment variable and is never written to a span or a log.

## Design choices in this POC

- **OpenTelemetry, not a vendor SDK.** The same spans go to a local file, to memory, and to LangSmith. Switching
  tools is a configuration change. This matches architecture rule 3.
- **Spans where decisions happen.** Retrieval, the LLM call, and the citation check, not every helper method.
- **Attributes that a dashboard can group by:** model name, prompt version, hit count, top score.
- **Simple span processing for the lab, batch processing for production.** The lab exports each span immediately so
  you can see it; a real service batches them in the background to avoid slowing requests.

## Gaps for production

- **No sampling.** Every request is traced. At high volume, sample (for example, keep all errors and 10% of successes).
- **No alerts.** The traces are there; the alerting rules and dashboards are not built.
- **No cost in currency.** Tokens are counted; converting them to money needs the provider's price list.
- **Only the RAG path is traced.** The augmented endpoint (`/poc03/ask-augmented`) and ingestion are not
  wrapped in spans.
- **Dependency alignment.** The Qdrant Java client is pinned to 1.19.0 to match the server (1.19.x). This removed
  the version warning. Keep the client within one minor version of the server, and re-check after each upgrade.
