package com.conceptgraph.lessons.poc05;

import com.conceptgraph.retrieval.Bm25Index;
import com.conceptgraph.retrieval.KeywordRetriever;
import com.conceptgraph.retrieval.VectorMath;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * POC 5, lesson 1: keyword search with BM25, no model at all.
 * GET  /poc05/bm25          ranked chunks, with each query term's share of the score
 * GET  /poc05/bm25/terms    how rare each query term is (document frequency and idf)
 * POST /poc05/bm25/rebuild  re-read the notes after editing one
 */
@RestController
@RequestMapping("/poc05")
public class Poc05Bm25Controller {

    private final KeywordRetriever keyword;

    public Poc05Bm25Controller(KeywordRetriever keyword) {
        this.keyword = keyword;
    }

    @Tag(name = "POC 5.1 BM25 keyword search", description = "Hand-written BM25 over the section chunks")
    @Operation(summary = "Rank chunks by BM25",
            description = "Look at termScores: which word earned the score? Try 'What is RRF?' (one rare word wins), "
                    + "then 'Why does the model make things up?' (no note says 'make things up': BM25 finds only "
                    + "weak matches on 'model'). Add category=Graph to filter by metadata.")
    @GetMapping("/bm25")
    public List<Map<String, Object>> search(@RequestParam(defaultValue = "What is RRF?") String query,
                                            @RequestParam(defaultValue = "5") int k,
                                            @RequestParam(required = false) String category) {
        return keyword.searchWithTerms(query, k, category).stream().map(Poc05Bm25Controller::row).toList();
    }

    @Tag(name = "POC 5.1 BM25 keyword search")
    @Operation(summary = "How rare is each query word?",
            description = "df = how many chunks contain the word; idf = its weight. Compare 'rrf' (rare, high idf) "
                    + "with 'model' (common, low idf). Stopwords such as 'what' and 'is' are dropped before this step.")
    @GetMapping("/bm25/terms")
    public Map<String, Object> terms(@RequestParam(defaultValue = "What is the model RRF?") String query) {
        Bm25Index index = keyword.index();
        Map<String, Object> terms = new LinkedHashMap<>();
        for (String term : Bm25Index.tokenize(query).stream().distinct().toList()) {
            terms.put(term, Map.of("df", index.docFreq(term), "idf", VectorMath.round(index.idf(term), 3)));
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("chunks", index.size());
        response.put("avgChunkLength", VectorMath.round(index.avgLength(), 1));
        response.put("terms", terms);
        return response;
    }

    @Tag(name = "POC 5.1 BM25 keyword search")
    @Operation(summary = "Rebuild the index from the notes on disk",
            description = "BM25 needs no embeddings, so a rebuild takes milliseconds. Qdrant still needs "
                    + "POST /poc03/ingest after a note changes.")
    @PostMapping("/bm25/rebuild")
    public Map<String, Object> rebuild() {
        return Map.of("chunks", keyword.rebuild());
    }

    private static Map<String, Object> row(Bm25Index.Hit h) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("chunkId", h.chunk().id());
        row.put("score", VectorMath.round(h.score(), 3));
        Map<String, Object> parts = new LinkedHashMap<>();
        h.termScores().forEach((term, s) -> parts.put(term, VectorMath.round(s, 3)));
        row.put("termScores", parts);
        row.put("text", h.chunk().text());
        return row;
    }
}
