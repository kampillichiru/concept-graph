package com.conceptgraph.lessons.poc04;

import com.conceptgraph.tracing.RecentSpanStore;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * POC 4, lesson 3: read the traces back.
 * GET /poc04/traces                 recent traces, newest first
 * GET /poc04/traces/{traceId}       one trace as an indented tree with timings
 * These read the in-memory span store. The same spans are in tutor/traces/spans.jsonl (the trace file).
 */
@RestController
@RequestMapping("/poc04/traces")
public class Poc04TraceController {

    private final RecentSpanStore store;

    public Poc04TraceController(RecentSpanStore store) {
        this.store = store;
    }

    @Tag(name = "POC 4.2 Read the traces", description = "Traces as trees, with durations and errors")
    @Operation(summary = "Recent traces",
            description = "One line per trace: the root span name, how many spans, total duration, and errors. "
                    + "Errors > 0 means a span ended with status ERROR (for example, Ollama was stopped).")
    @GetMapping
    public List<Map<String, Object>> traces() {
        Map<String, List<SpanData>> byTrace = groupByTrace();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<SpanData>> e : byTrace.entrySet()) {
            List<SpanData> spans = e.getValue();
            SpanData root = roots(spans).stream().findFirst().orElse(spans.get(0));
            long start = spans.stream().mapToLong(SpanData::getStartEpochNanos).min().orElse(0);
            long end = spans.stream().mapToLong(SpanData::getEndEpochNanos).max().orElse(0);
            long errors = spans.stream().filter(s -> s.getStatus().getStatusCode().name().equals("ERROR")).count();

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("traceId", e.getKey());
            row.put("root", root.getName());
            row.put("spans", spans.size());
            row.put("durationMs", (end - start) / 1_000_000);
            row.put("errors", errors);
            row.put("_start", start);
            result.add(row);
        }
        result.sort(Comparator.comparingLong((Map<String, Object> r) -> (long) r.get("_start")).reversed());
        result.forEach(r -> r.remove("_start"));
        return result;
    }

    @Tag(name = "POC 4.2 Read the traces", description = "Traces as trees, with durations and errors")
    @Operation(summary = "One trace as a tree",
            description = "'lines' shows the parent/child structure with indentation. 'spans' has the attributes "
                    + "(gen_ai.* for LLM calls, rag.* for retrieval). Find the slowest span: that is where time went.")
    @GetMapping("/{traceId}")
    public Map<String, Object> trace(@PathVariable String traceId) {
        List<SpanData> spans = groupByTrace().getOrDefault(traceId, List.of());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("traceId", traceId);
        if (spans.isEmpty()) {
            result.put("error", "No spans for this trace id in memory. Only the last ~1000 spans are kept; "
                    + "the trace file keeps everything.");
            return result;
        }
        List<String> lines = new ArrayList<>();
        List<Map<String, Object>> details = new ArrayList<>();
        Map<String, List<SpanData>> children = childrenOf(spans);
        for (SpanData root : roots(spans)) {
            walk(root, 0, children, lines, details);
        }
        result.put("lines", lines);
        result.put("spans", details);
        return result;
    }

    private void walk(SpanData span, int depth, Map<String, List<SpanData>> children,
                      List<String> lines, List<Map<String, Object>> details) {
        long ms = (span.getEndEpochNanos() - span.getStartEpochNanos()) / 1_000_000;
        String status = span.getStatus().getStatusCode().name();
        lines.add("  ".repeat(depth) + span.getName() + "  (" + ms + " ms)"
                + ("ERROR".equals(status) ? "  <-- ERROR" : ""));

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("depth", depth);
        detail.put("name", span.getName());
        detail.put("durationMs", ms);
        detail.put("status", status);
        Map<String, String> attributes = new TreeMap<>();
        span.getAttributes().forEach((key, value) -> attributes.put(key.getKey(), String.valueOf(value)));
        detail.put("attributes", attributes);
        details.add(detail);

        for (SpanData child : children.getOrDefault(span.getSpanId(), List.of())) {
            walk(child, depth + 1, children, lines, details);
        }
    }

    private Map<String, List<SpanData>> groupByTrace() {
        Map<String, List<SpanData>> byTrace = new LinkedHashMap<>();
        for (SpanData span : store.snapshot()) {
            byTrace.computeIfAbsent(span.getTraceId(), id -> new ArrayList<>()).add(span);
        }
        byTrace.values().forEach(list -> list.sort(Comparator.comparingLong(SpanData::getStartEpochNanos)));
        return byTrace;
    }

    /** Spans whose parent is not part of this trace: normally just the one root. */
    private static List<SpanData> roots(List<SpanData> spans) {
        List<String> ids = spans.stream().map(SpanData::getSpanId).toList();
        return spans.stream()
                .filter(s -> SpanId.getInvalid().equals(s.getParentSpanId()) || !ids.contains(s.getParentSpanId()))
                .toList();
    }

    private static Map<String, List<SpanData>> childrenOf(List<SpanData> spans) {
        Map<String, List<SpanData>> children = new LinkedHashMap<>();
        for (SpanData span : spans) {
            if (!SpanId.getInvalid().equals(span.getParentSpanId())) {
                children.computeIfAbsent(span.getParentSpanId(), id -> new ArrayList<>()).add(span);
            }
        }
        return children;
    }
}
