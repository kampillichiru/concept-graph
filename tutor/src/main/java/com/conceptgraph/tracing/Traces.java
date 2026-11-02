package com.conceptgraph.tracing;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

import java.util.function.Function;

/**
 * Runs a block inside a span. The span is the "current" span while the block runs, so any span created
 * inside becomes its child. That parent/child link is what makes a trace a tree.
 */
public final class Traces {

    private Traces() {
    }

    public static <T> T inSpan(Tracer tracer, String name, Function<Span, T> body) {
        Span span = tracer.spanBuilder(name).setSpanKind(SpanKind.INTERNAL).startSpan();
        try (Scope ignored = span.makeCurrent()) {
            return body.apply(span);
        } catch (RuntimeException e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, String.valueOf(e.getMessage()));
            throw e;
        } finally {
            span.end();
        }
    }
}
