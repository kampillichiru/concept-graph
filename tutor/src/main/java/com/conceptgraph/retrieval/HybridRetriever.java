package com.conceptgraph.retrieval;

import com.conceptgraph.retrieval.ReciprocalRankFusion.Fused;
import com.conceptgraph.tracing.Traces;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hybrid search (POC 5.2): run BM25 and vector search on the same question, then fuse with RRF.
 * Each side fetches more candidates than we return (CANDIDATES), so a chunk ranked 12th by one side
 * and 2nd by the other can still win.
 */
@Component
public class HybridRetriever {

    public static final int CANDIDATES = 20;

    private final VectorRetriever vector;
    private final KeywordRetriever keyword;
    private final Tracer tracer;

    public HybridRetriever(VectorRetriever vector, KeywordRetriever keyword, Tracer tracer) {
        this.vector = vector;
        this.keyword = keyword;
        this.tracer = tracer;
    }

    public List<RetrievedChunk> search(String query, int k, String category) {
        return searchWithRanks(query, k, category).stream().map(Fused::chunk).toList();
    }

    public List<Fused> searchWithRanks(String query, int k, String category) {
        return Traces.inSpan(tracer, "retrieve.hybrid", span -> {
            Map<String, List<RetrievedChunk>> lists = new LinkedHashMap<>();
            lists.put("vector", vector.search(query, CANDIDATES, category));
            lists.put("bm25", keyword.search(query, CANDIDATES, category));
            List<Fused> fused = ReciprocalRankFusion.fuse(lists, ReciprocalRankFusion.K, k);
            span.setAttribute("retrieve.hits", fused.size());
            return fused;
        });
    }
}
