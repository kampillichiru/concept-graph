package com.conceptgraph.lessons.poc02;

import com.conceptgraph.lessons.poc02.SentenceIndex.Sentence;
import com.conceptgraph.retrieval.VectorMath;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * POC 2, lessons 3 and 4: semantic search vs keyword search, and the nomic prefixes.
 * GET /poc02/search    nearest neighbours in the vector store, next to a simple keyword search
 * GET /poc02/prefixes  break it: the same search with and without search_query:/search_document:
 */
@RestController
@RequestMapping("/poc02")
public class Poc02SearchController {

    private final SentenceIndex index;

    public Poc02SearchController(SentenceIndex index) {
        this.index = index;
    }

    @Tag(name = "POC 2.3 Semantic search", description = "Search by meaning (vectors) vs search by words (keywords)")
    @Operation(summary = "Semantic search next to keyword search",
            description = "Try: 'coffee', 'software that works autonomously', 'holiday destinations', "
                    + "'how do I find similar text?'. Where does keyword search find nothing, while semantic search succeeds?")
    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam(defaultValue = "coffee") String query,
                                      @RequestParam(defaultValue = "3") int k) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", query);
        result.put("semantic", semantic(query, k, true));
        result.put("keyword", keyword(query, k));
        return result;
    }

    @Tag(name = "POC 2.4 Break it: prefixes", description = "nomic-embed-text expects task prefixes")
    @Operation(summary = "Same search with and without nomic's prefixes",
            description = "nomic-embed-text was trained with 'search_query: ' on questions and 'search_document: ' "
                    + "on documents. Compare scores and ranking. Small details in HOW you embed change WHAT you find.")
    @GetMapping("/prefixes")
    public Map<String, Object> prefixes(@RequestParam(defaultValue = "What is a programming language?") String query,
                                        @RequestParam(defaultValue = "5") int k) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", query);
        result.put("withPrefixes", semantic(query, k, true));
        result.put("withoutPrefixes", semantic(query, k, false));
        return result;
    }

    /** Vector search: embed the query, find the k nearest sentence vectors. */
    private List<Map<String, Object>> semantic(String query, int k, boolean prefixed) {
        Embedding queryEmbedding = index.embedQuery(query, prefixed);
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(k)
                .build();
        List<EmbeddingMatch<TextSegment>> matches = index.store(prefixed).search(request).matches();

        return matches.stream().map(m -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", m.embedded().metadata().getString("id"));
            row.put("text", m.embedded().text());
            // LangChain4j stores return a relevance score = (cosine + 1) / 2, so 0.5 means cosine 0
            row.put("score", VectorMath.round(m.score(), 3));
            row.put("cosine", VectorMath.round(2 * m.score() - 1, 3));
            return row;
        }).toList();
    }

    private static final Set<String> STOP_WORDS = Set.of("a", "an", "the", "is", "are", "and", "or", "of", "to", "in",
            "on", "for", "with", "what", "how", "do", "does", "i", "it", "its", "my", "can", "which", "that", "this",
            "be", "by", "from", "me", "you", "every", "into", "s");

    /** Keyword search: score = fraction of query words that appear in the sentence. No embeddings. */
    private List<Map<String, Object>> keyword(String query, int k) {
        Set<String> queryWords = words(query);
        if (queryWords.isEmpty()) {
            return List.of();
        }
        return index.sentences().stream()
                .map(s -> Map.entry(s, overlap(queryWords, words(s.text()))))
                .filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<Sentence, Double>comparingByValue().reversed())
                .limit(k)
                .map(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", e.getKey().id());
                    row.put("text", e.getKey().text());
                    row.put("score", VectorMath.round(e.getValue(), 3));
                    return row;
                })
                .toList();
    }

    private static Set<String> words(String text) {
        return Arrays.stream(text.toLowerCase().split("[^a-z0-9]+"))
                .filter(w -> !w.isBlank() && !STOP_WORDS.contains(w))
                .collect(Collectors.toSet());
    }

    private static double overlap(Set<String> queryWords, Set<String> sentenceWords) {
        long shared = queryWords.stream().filter(sentenceWords::contains).count();
        return (double) shared / queryWords.size();
    }
}
