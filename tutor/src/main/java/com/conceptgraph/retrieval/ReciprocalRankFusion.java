package com.conceptgraph.retrieval;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RRF (POC 5.2): merge ranked lists using positions only.
 * A chunk scores 1 / (k + rank) in each list it appears in; the scores are summed.
 * Cosine (0.7-0.9) and BM25 (0-15) live on different scales, so adding raw scores would let BM25 dominate.
 * Ranks have no scale, so the fusion needs no tuning beyond k.
 */
public final class ReciprocalRankFusion {

    /** The usual default. Larger k flattens the difference between rank 1 and rank 10. */
    public static final int K = 60;

    /** A fused result, with the rank it had in each input list (absent = not in that list). */
    public record Fused(RetrievedChunk chunk, double rrfScore, Map<String, Integer> ranks) {
    }

    private ReciprocalRankFusion() {
    }

    /** @param lists name -> ranked results, for example "vector" -> [...], "bm25" -> [...] */
    public static List<Fused> fuse(Map<String, List<RetrievedChunk>> lists, int k, int topN) {
        Map<String, Double> scores = new LinkedHashMap<>();
        Map<String, RetrievedChunk> byId = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> ranks = new LinkedHashMap<>();

        lists.forEach((listName, results) -> {
            for (int i = 0; i < results.size(); i++) {
                RetrievedChunk c = results.get(i);
                int rank = i + 1;                                      // ranks start at 1
                scores.merge(c.chunkId(), 1.0 / (k + rank), Double::sum);
                byId.putIfAbsent(c.chunkId(), c);
                ranks.computeIfAbsent(c.chunkId(), id -> new LinkedHashMap<>()).put(listName, rank);
            }
        });

        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
                .limit(topN)
                .map(e -> new Fused(byId.get(e.getKey()).withScore(e.getValue()), e.getValue(), ranks.get(e.getKey())))
                .toList();
    }
}
