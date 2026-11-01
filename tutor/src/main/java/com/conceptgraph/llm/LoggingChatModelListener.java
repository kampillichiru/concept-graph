package com.conceptgraph.llm;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A minimal trace of every LLM call (model, tokens, latency), logged to stderr.
 * POC 4 replaces this with OpenTelemetry spans sent to LangSmith, using the same hook.
 */
public class LoggingChatModelListener implements ChatModelListener {

    private static final Logger log = LoggerFactory.getLogger("llm-trace");
    private static final String START = "startNanos";

    @Override
    public void onRequest(ChatModelRequestContext ctx) {
        ctx.attributes().put(START, System.nanoTime());
    }

    @Override
    public void onResponse(ChatModelResponseContext ctx) {
        long ms = (System.nanoTime() - (long) ctx.attributes().get(START)) / 1_000_000;
        ChatResponse response = ctx.chatResponse();
        TokenUsage usage = response.tokenUsage();
        log.info("model={} inputTokens={} outputTokens={} latencyMs={}",
                response.modelName(),
                usage == null ? "?" : usage.inputTokenCount(),
                usage == null ? "?" : usage.outputTokenCount(),
                ms);
    }

    @Override
    public void onError(ChatModelErrorContext ctx) {
        log.warn("LLM call failed: {}", ctx.error().toString());
    }
}
