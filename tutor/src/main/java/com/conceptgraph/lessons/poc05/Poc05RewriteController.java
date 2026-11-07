package com.conceptgraph.lessons.poc05;

import com.conceptgraph.retrieval.HybridRetriever;
import com.conceptgraph.retrieval.QueryRewriter;
import com.conceptgraph.tracing.Traces;
import io.opentelemetry.api.trace.Tracer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * POC 5, lesson 3: query rewriting. The LLM names the terms a vague question means; we search with
 * the original plus those terms.
 * GET /poc05/rewrite  hybrid results before and after the rewrite
 */
@RestController
@RequestMapping("/poc05")
public class Poc05RewriteController {

    private final QueryRewriter rewriter;
    private final HybridRetriever hybrid;
    private final Tracer tracer;

    public Poc05RewriteController(QueryRewriter rewriter, HybridRetriever hybrid, Tracer tracer) {
        this.rewriter = rewriter;
        this.hybrid = hybrid;
        this.tracer = tracer;
    }

    @Tag(name = "POC 5.3 Query rewriting", description = "LLM turns a vague question into search terms")
    @Operation(summary = "Search before and after rewriting the question",
            description = "Try 'that graph thing with LLMs': which concept is on top before, and after? "
                    + "Then try a question that is already precise ('What is RRF?'): did the rewrite help, or "
                    + "only cost an LLM call? Compare rewriteMs with searchMs: the rewrite is the expensive step. "
                    + "Break it: promptVersion=query-rewrite-v1 (the model copies the prompt's topic list).")
    @GetMapping("/rewrite")
    public Map<String, Object> rewrite(@RequestParam(defaultValue = "that graph thing with LLMs") String query,
                                       @RequestParam(defaultValue = "3") int k,
                                       @RequestParam(defaultValue = QueryRewriter.PROMPT_VERSION) String promptVersion) {
        return Traces.inSpan(tracer, "poc05.rewrite", span -> {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("traceId", span.getSpanContext().getTraceId());
            response.put("original", query);

            long t0 = System.nanoTime();
            response.put("before", Poc05HybridController.labels(hybrid.search(query, k, null)));
            long t1 = System.nanoTime();
            String keywords = rewriter.keywords(query, promptVersion);
            response.put("promptVersion", promptVersion);
            long t2 = System.nanoTime();
            String expanded = query + " " + keywords;
            response.put("keywords", keywords);
            response.put("expanded", expanded);
            response.put("after", Poc05HybridController.labels(hybrid.search(expanded, k, null)));

            response.put("searchMs", (t1 - t0) / 1_000_000);
            response.put("rewriteMs", (t2 - t1) / 1_000_000);
            return response;
        });
    }
}
