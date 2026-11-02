package com.conceptgraph.tracing;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.StringJoiner;
import java.util.TreeMap;

/**
 * The offline trace: one JSON object per line, one line per finished span.
 * You can open the file in any text editor, grep it, or load it into a notebook. No account or network needed.
 * Writes JSON by hand on purpose, so the mechanics stay visible and no extra library is needed.
 */
public class JsonlSpanExporter implements SpanExporter {

    private final Path file;

    public JsonlSpanExporter(Path file) {
        this.file = file;
    }

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        StringBuilder lines = new StringBuilder();
        for (SpanData span : spans) {
            lines.append(toJson(span)).append('\n');
        }
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, lines.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return CompletableResultCode.ofSuccess();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write trace file " + file.toAbsolutePath(), e);
        }
    }

    @Override
    public CompletableResultCode flush() {
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
        return CompletableResultCode.ofSuccess();
    }

    static String toJson(SpanData span) {
        StringJoiner attrs = new StringJoiner(",", "{", "}");
        new TreeMap<>(attributes(span)).forEach((k, v) -> attrs.add(quote(k) + ":" + quote(v)));
        return "{"
                + "\"traceId\":" + quote(span.getTraceId())
                + ",\"spanId\":" + quote(span.getSpanId())
                + ",\"parentSpanId\":" + quote(span.getParentSpanId())
                + ",\"name\":" + quote(span.getName())
                + ",\"startNanos\":" + span.getStartEpochNanos()
                + ",\"endNanos\":" + span.getEndEpochNanos()
                + ",\"status\":" + quote(span.getStatus().getStatusCode().name())
                + ",\"attributes\":" + attrs
                + "}";
    }

    private static java.util.Map<String, String> attributes(SpanData span) {
        java.util.Map<String, String> result = new java.util.LinkedHashMap<>();
        span.getAttributes().forEach((AttributeKey<?> key, Object value) -> result.put(key.getKey(), String.valueOf(value)));
        return result;
    }

    /** Minimal JSON string escaping: quotes, backslashes and control characters. */
    static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : String.valueOf(s).toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
