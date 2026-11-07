package com.conceptgraph.lessons.poc05;

import com.conceptgraph.retrieval.HybridRetriever;
import com.conceptgraph.retrieval.LlmReranker;
import com.conceptgraph.retrieval.LlmReranker.Reranked;
import com.conceptgraph.retrieval.RetrievedChunk;
import com.conceptgraph.tracing.Traces;
import io.opentelemetry.api.trace.Tracer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * POC 5, lesson 4: rerank the hybrid candidates with the LLM (one listwise call).
 * GET /poc05/rerank  hybrid order vs reranked order, the model's raw reply, and the cost
 */
@RestController
@RequestMapping("/poc05")
public class Poc05RerankController {

    private final HybridRetriever hybrid;
    private final LlmReranker reranker;
    private final Tracer tracer;

    public Poc05RerankController(HybridRetriever hybrid, LlmReranker reranker, Tracer tracer) {
        this.hybrid = hybrid;
        this.reranker = reranker;
        this.tracer = tracer;
    }

    @Tag(name = "POC 5.4 Reranking", description = "LLM as a listwise reranker over the hybrid candidates")
    @Operation(summary = "Rerank the top hybrid candidates",
            description = "Compare hybridOrder with rerankedOrder, and read rawReply: did the model return a real "
                    + "order, or just '1,2,3,4,...'? A lazy reply means rerank = hybrid, at the price of an LLM call. "
                    + "Compare rerankMs with hybridMs.")
    @GetMapping("/rerank")
    public Map<String, Object> rerank(
            @RequestParam(defaultValue = "the second pass that sorts search results again") String query,
            @RequestParam(defaultValue = "3") int k) {
        return Traces.inSpan(tracer, "poc05.rerank", span -> {
            long t0 = System.nanoTime();
            List<RetrievedChunk> candidates = hybrid.search(query, LlmReranker.CANDIDATES, null);
            long t1 = System.nanoTime();
            Reranked reranked = reranker.rerank(query, candidates);
            long t2 = System.nanoTime();

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("traceId", span.getSpanContext().getTraceId());
            response.put("query", query);
            response.put("hybridOrder", ids(candidates));
            response.put("rawReply", reranked.rawReply());
            response.put("modelOrder", reranked.modelOrder());
            response.put("rerankedOrder", ids(reranked.chunks()));
            response.put("topK", ids(reranked.chunks().subList(0, Math.min(k, reranked.chunks().size()))));
            response.put("hybridMs", (t1 - t0) / 1_000_000);
            response.put("rerankMs", (t2 - t1) / 1_000_000);
            return response;
        });
    }

    private static List<String> ids(List<RetrievedChunk> chunks) {
        return chunks.stream().map(RetrievedChunk::chunkId).toList();
    }
}
