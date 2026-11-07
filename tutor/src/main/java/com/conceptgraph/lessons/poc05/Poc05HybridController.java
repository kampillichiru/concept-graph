package com.conceptgraph.lessons.poc05;

import com.conceptgraph.retrieval.HybridRetriever;
import com.conceptgraph.retrieval.KeywordRetriever;
import com.conceptgraph.retrieval.ReciprocalRankFusion.Fused;
import com.conceptgraph.retrieval.RetrievedChunk;
import com.conceptgraph.retrieval.VectorMath;
import com.conceptgraph.retrieval.VectorRetriever;
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
 * POC 5, lesson 2: hybrid search. Vector and BM25 side by side, then fused with RRF.
 * GET /poc05/hybrid  the three ranked lists for one question, with each fused chunk's rank in each input list
 */
@RestController
@RequestMapping("/poc05")
public class Poc05HybridController {

    private final VectorRetriever vector;
    private final KeywordRetriever keyword;
    private final HybridRetriever hybrid;

    public Poc05HybridController(VectorRetriever vector, KeywordRetriever keyword, HybridRetriever hybrid) {
        this.vector = vector;
        this.keyword = keyword;
        this.hybrid = hybrid;
    }

    @Tag(name = "POC 5.2 Hybrid search and RRF", description = "Vector + BM25 merged by rank, with a metadata filter")
    @Operation(summary = "Compare vector, BM25 and hybrid for one question",
            description = "Read 'hybrid[].ranks': a chunk 1st in bm25 and 4th in vector scores 1/61 + 1/64. "
                    + "Try 'What is RRF?' (BM25 is sure, vector is not) and 'Why does the model make things up?' "
                    + "(the other way round). Does hybrid get both right? Add category=Graph: the filter runs "
                    + "inside Qdrant (payload filter) and inside BM25, before ranking.")
    @GetMapping("/hybrid")
    public Map<String, Object> compare(@RequestParam(defaultValue = "What is RRF?") String query,
                                       @RequestParam(defaultValue = "5") int k,
                                       @RequestParam(required = false) String category) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("query", query);
        response.put("category", category);
        response.put("vector", labels(vector.search(query, k, category)));
        response.put("bm25", labels(keyword.search(query, k, category)));
        response.put("hybrid", hybrid.searchWithRanks(query, k, category).stream()
                .map(Poc05HybridController::fusedRow).toList());
        response.put("note", "hybrid fuses the top " + HybridRetriever.CANDIDATES + " of each list, so a chunk "
                + "can appear in hybrid with a rank > " + k + " in one input list.");
        return response;
    }

    static List<String> labels(List<RetrievedChunk> chunks) {
        return chunks.stream().map(c -> c.chunkId() + "  (" + VectorMath.round(c.score(), 3) + ")").toList();
    }

    private static Map<String, Object> fusedRow(Fused f) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("chunkId", f.chunk().chunkId());
        row.put("rrfScore", VectorMath.round(f.rrfScore(), 5));
        row.put("ranks", f.ranks());
        return row;
    }
}
