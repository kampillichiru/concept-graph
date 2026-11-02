package com.conceptgraph.tracing;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Keeps the last spans in memory so the /poc04 endpoints can show them without reading a file.
 * Bounded: old spans drop off the front, so memory never grows without limit.
 */
public class RecentSpanStore implements SpanExporter {

    private static final int MAX_SPANS = 1000;

    private final List<SpanData> spans = new ArrayList<>();

    @Override
    public synchronized CompletableResultCode export(Collection<SpanData> batch) {
        spans.addAll(batch);
        if (spans.size() > MAX_SPANS) {
            spans.subList(0, spans.size() - MAX_SPANS).clear();
        }
        return CompletableResultCode.ofSuccess();
    }

    /** A copy, so callers can read it while new spans arrive. */
    public synchronized List<SpanData> snapshot() {
        return List.copyOf(spans);
    }

    @Override
    public CompletableResultCode flush() {
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
        return CompletableResultCode.ofSuccess();
    }
}
