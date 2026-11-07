package com.conceptgraph.retrieval;

import com.conceptgraph.knowledge.Chunk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * BM25 keyword search, written by hand (POC 5.1). No model, no vectors: only word counts.
 * <pre>
 * score(doc, query) = sum over query terms t of  idf(t) * tf * (k1 + 1) / (tf + k1 * (1 - b + b * len / avgLen))
 * </pre>
 * idf: rare words count more. The tf fraction saturates: the 5th "graph" adds less than the 1st.
 * The len / avgLen part stops long chunks from winning just because they contain more words.
 */
public final class Bm25Index {

    /** One match, with each query term's share of the score so you can see why it ranked. */
    public record Hit(Chunk chunk, double score, Map<String, Double> termScores) {
    }

    static final double K1 = 1.2;   // how fast term frequency saturates (1.2-2.0 is usual)
    static final double B = 0.75;   // how much length is normalised (0 = not at all, 1 = fully)

    /** Words that carry no topic. Without this list, "what is" would match every chunk a little. */
    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "the", "is", "are", "be", "what", "how", "why", "which", "when", "does", "do", "can",
            "of", "to", "in", "on", "at", "by", "for", "from", "with", "into", "and", "or", "not",
            "it", "its", "this", "that", "i", "me", "my", "we", "you", "your");

    private final List<Chunk> chunks;
    private final List<Map<String, Integer>> termFreqs = new ArrayList<>(); // per chunk: term -> count
    private final int[] lengths;                                           // per chunk: number of terms
    private final Map<String, Integer> docFreq = new HashMap<>();          // term -> chunks containing it
    private final double avgLength;

    public Bm25Index(List<Chunk> chunks) {
        this.chunks = List.copyOf(chunks);
        this.lengths = new int[chunks.size()];
        long total = 0;
        for (int i = 0; i < chunks.size(); i++) {
            List<String> tokens = tokenize(indexText(chunks.get(i)));
            Map<String, Integer> tf = new HashMap<>();
            tokens.forEach(t -> tf.merge(t, 1, Integer::sum));
            termFreqs.add(tf);
            lengths[i] = tokens.size();
            total += tokens.size();
            tf.keySet().forEach(t -> docFreq.merge(t, 1, Integer::sum));
        }
        this.avgLength = chunks.isEmpty() ? 0 : (double) total / chunks.size();
    }

    public List<Hit> search(String query, int k, Predicate<Chunk> filter) {
        List<String> terms = tokenize(query).stream().distinct().toList();
        List<Hit> hits = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            if (!filter.test(chunks.get(i))) {
                continue;
            }
            Map<String, Double> parts = new LinkedHashMap<>();
            for (String term : terms) {
                int tf = termFreqs.get(i).getOrDefault(term, 0);
                if (tf > 0) {
                    double norm = K1 * (1 - B + B * lengths[i] / avgLength);
                    parts.put(term, idf(term) * tf * (K1 + 1) / (tf + norm));
                }
            }
            if (!parts.isEmpty()) {
                double score = parts.values().stream().mapToDouble(Double::doubleValue).sum();
                hits.add(new Hit(chunks.get(i), score, parts));
            }
        }
        return hits.stream().sorted(Comparator.comparingDouble(Hit::score).reversed()).limit(k).toList();
    }

    /** Lucene's idf: log(1 + (N - n + 0.5) / (n + 0.5)). A word in every chunk scores near 0, never below. */
    public double idf(String term) {
        int n = docFreq.getOrDefault(term, 0);
        return Math.log(1 + (chunks.size() - n + 0.5) / (n + 0.5));
    }

    public int docFreq(String term) {
        return docFreq.getOrDefault(term, 0);
    }

    public int size() {
        return chunks.size();
    }

    public double avgLength() {
        return avgLength;
    }

    /** Same text the vector side embeds: title + section, so "RRF" in the title counts for every section. */
    static String indexText(Chunk c) {
        return c.title() + "\n" + c.text();
    }

    /** Lower case, split on anything that is not a letter or digit, drop stopwords. No stemming (see lesson). */
    public static List<String> tokenize(String text) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(t -> !t.isEmpty() && !STOPWORDS.contains(t))
                .toList();
    }
}
