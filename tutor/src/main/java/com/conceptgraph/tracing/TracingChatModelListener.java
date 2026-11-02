package com.conceptgraph.tracing;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.stereotype.Component;

/**
 * One span per LLM call, using the LangChain4j listener hook (POC 4.1).
 * onRequest starts the span, onResponse or onError ends it. The span is stored in the request context,
 * because the same call's response arrives later, in a different method.
 * Attribute names follow the OpenTelemetry GenAI conventions (gen_ai.*), which LangSmith reads.
 */
@Component
public class TracingChatModelListener implements ChatModelListener {

    private static final String SPAN_KEY = "tracing.chatSpan";

    private final Tracer tracer;

    public TracingChatModelListener(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public void onRequest(ChatModelRequestContext ctx) {
        String model = modelName(ctx.chatRequest().parameters() == null ? null : ctx.chatRequest().parameters().modelName());
        Span span = tracer.spanBuilder("chat " + model)
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("gen_ai.system", "ollama")
                .setAttribute("gen_ai.operation.name", "chat")
                .setAttribute("gen_ai.request.model", model)
                .setAttribute("gen_ai.request.message_count", ctx.chatRequest().messages().size())
                .startSpan();
        ctx.attributes().put(SPAN_KEY, span);
    }

    @Override
    public void onResponse(ChatModelResponseContext ctx) {
        Span span = (Span) ctx.attributes().get(SPAN_KEY);
        if (span == null) {
            return;
        }
        ChatResponse response = ctx.chatResponse();
        span.setAttribute("gen_ai.response.model", modelName(response.modelName()));
        if (response.finishReason() != null) {
            span.setAttribute("gen_ai.response.finish_reason", response.finishReason().name());
        }
        TokenUsage usage = response.tokenUsage();
        if (usage != null) {
            span.setAttribute("gen_ai.usage.input_tokens", usage.inputTokenCount() == null ? 0 : usage.inputTokenCount());
            span.setAttribute("gen_ai.usage.output_tokens", usage.outputTokenCount() == null ? 0 : usage.outputTokenCount());
        }
        span.end();
    }

    @Override
    public void onError(ChatModelErrorContext ctx) {
        Span span = (Span) ctx.attributes().get(SPAN_KEY);
        if (span == null) {
            return;
        }
        span.recordException(ctx.error());
        span.setStatus(StatusCode.ERROR, String.valueOf(ctx.error().getMessage()));
        span.end();
    }

    private static String modelName(String name) {
        return name == null ? "unknown" : name;
    }
}
