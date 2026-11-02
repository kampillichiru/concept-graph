package com.conceptgraph.tracing;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.listener.EmbeddingModelErrorContext;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.embedding.listener.EmbeddingModelRequestContext;
import dev.langchain4j.model.embedding.listener.EmbeddingModelResponseContext;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * One span per embedding call (POC 4.1): the question embedding, every ingestion batch, every augmentor search.
 * Records how many texts and characters went in, so you can see the cost of an embedding step.
 */
@Component
public class TracingEmbeddingModelListener implements EmbeddingModelListener {

    private static final String SPAN_KEY = "tracing.embedSpan";

    private final Tracer tracer;

    public TracingEmbeddingModelListener(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public void onRequest(EmbeddingModelRequestContext ctx) {
        String model = ctx.embeddingModel().modelName();
        List<TextSegment> segments = ctx.textSegments() == null ? List.of() : ctx.textSegments();
        long chars = segments.stream().mapToLong(s -> s.text().length()).sum();
        Span span = tracer.spanBuilder("embed " + model)
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("gen_ai.system", "ollama")
                .setAttribute("gen_ai.operation.name", "embeddings")
                .setAttribute("gen_ai.request.model", model)
                .setAttribute("embed.inputs", segments.size())
                .setAttribute("embed.chars", chars)
                .startSpan();
        ctx.attributes().put(SPAN_KEY, span);
    }

    @Override
    public void onResponse(EmbeddingModelResponseContext ctx) {
        Span span = (Span) ctx.attributes().get(SPAN_KEY);
        if (span == null) {
            return;
        }
        var vectors = ctx.response().content();
        if (vectors != null && !vectors.isEmpty()) {
            span.setAttribute("embed.dimension", vectors.get(0).dimension());
        }
        span.end();
    }

    @Override
    public void onError(EmbeddingModelErrorContext ctx) {
        Span span = (Span) ctx.attributes().get(SPAN_KEY);
        if (span == null) {
            return;
        }
        span.recordException(ctx.error());
        span.setStatus(StatusCode.ERROR, String.valueOf(ctx.error().getMessage()));
        span.end();
    }
}
