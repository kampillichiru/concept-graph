package com.conceptgraph.evals;

import com.conceptgraph.evals.GoldenSet.GoldenQuestion;
import com.conceptgraph.retrieval.HybridRetriever;
import com.conceptgraph.retrieval.KeywordRetriever;
import com.conceptgraph.retrieval.LlmReranker;
import com.conceptgraph.retrieval.QueryRewriter;
import com.conceptgraph.retrieval.RetrievedChunk;
import com.conceptgraph.retrieval.VectorRetriever;
import com.conceptgraph.tracing.Traces;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The retrieval eval (POC 5.5): every method answers every golden question; code scores the ranks.
 * No LLM judges anything here: "is the expected concept at rank r?" is a lookup, so the numbers are exact.
 * Each (question, method) pair is its own trace ("eval.case"), so a bad row can be opened in the trace view.
 */
@Component
public class RetrievalEval {

    /** How deep we look. MRR is MRR@10: a concept found at rank 11 counts as not found. */
    public static final int DEPTH = 10;

    public enum Method { VECTOR, BM25, HYBRID, HYBRID_RERANK, REWRITE_HYBRID }

    /** One method's result for one question. rank = 1-based rank of the first chunk of the expected concept, 0 = missed. */
    public record CaseResult(Method method, int rank, long millis, List<String> top3) {
    }

    private final GoldenSet goldenSet;
    private final VectorRetriever vector;
    private final KeywordRetriever keyword;
    private final HybridRetriever hybrid;
    private final LlmReranker reranker;
    private final QueryRewriter rewriter;
    private final Tracer tracer;

    public RetrievalEval(GoldenSet goldenSet, VectorRetriever vector, KeywordRetriever keyword,
                         HybridRetriever hybrid, LlmReranker reranker, QueryRewriter rewriter, Tracer tracer) {
        this.goldenSet = goldenSet;
        this.vector = vector;
        this.keyword = keyword;
        this.hybrid = hybrid;
        this.reranker = reranker;
        this.rewriter = rewriter;
        this.tracer = tracer;
    }

    /** The one switch that defines each method. Everything else in the eval is identical across methods. */
    public List<RetrievedChunk> search(Method method, String question, int k) {
        return switch (method) {
            case VECTOR -> vector.search(question, k, null);
            case BM25 -> keyword.search(question, k, null);
            case HYBRID -> hybrid.search(question, k, null);
            case HYBRID_RERANK -> reranker.rerank(question, hybrid.search(question, k, null)).chunks();
            case REWRITE_HYBRID -> hybrid.search(rewriter.expand(question), k, null);
        };
    }

    public Map<String, Object> run(List<Method> methods) {
        List<GoldenQuestion> questions = goldenSet.load();
        Map<Method, List<CaseResult>> byMethod = new LinkedHashMap<>();
        methods.forEach(m -> byMethod.put(m, new ArrayList<>()));
        List<Map<String, Object>> rows = new ArrayList<>();

        for (GoldenQuestion q : questions) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("question", q.question());
            row.put("expected", q.expectedConceptId());
            row.put("kind", q.kind());
            for (Method method : methods) {
                CaseResult result = runCase(method, q);
                byMethod.get(method).add(result);
                row.put(method.name(), Map.of("rank", result.rank(), "ms", result.millis(), "top3", result.top3()));
            }
            rows.add(row);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        byMethod.forEach((method, results) -> summary.put(method.name(), summarise(questions, results)));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("questions", questions.size());
        response.put("depth", DEPTH);
        response.put("summary", summary);
        response.put("markdown", markdown(summary));
        response.put("rows", rows);
        return response;
    }

    private CaseResult runCase(Method method, GoldenQuestion q) {
        return Traces.inSpan(tracer, "eval.case", span -> {
            span.setAttribute("eval.method", method.name());
            span.setAttribute("eval.question", q.question());
            long start = System.nanoTime();
            List<RetrievedChunk> results = search(method, q.question(), DEPTH);
            long millis = (System.nanoTime() - start) / 1_000_000;
            int rank = rankOf(results, q.expectedConceptId());
            span.setAttribute("eval.rank", rank);
            List<String> top3 = results.stream().limit(3).map(RetrievedChunk::chunkId).toList();
            return new CaseResult(method, rank, millis, top3);
        });
    }

    /** 1-based rank of the first chunk from the expected concept, or 0 if it is not in the list. */
    static int rankOf(List<RetrievedChunk> results, String expectedConceptId) {
        for (int i = 0; i < results.size(); i++) {
            if (results.get(i).conceptId().equals(expectedConceptId)) {
                return i + 1;
            }
        }
        return 0;
    }

    static boolean hitAt(int rank, int k) {
        return rank > 0 && rank <= k;
    }

    /** Reciprocal rank: 1 at rank 1, 0.5 at rank 2, 0.33 at rank 3 ... 0 when missed. MRR is its mean. */
    static double reciprocal(int rank) {
        return rank == 0 ? 0 : 1.0 / rank;
    }

    private static Map<String, Object> summarise(List<GoldenQuestion> questions, List<CaseResult> results) {
        int n = results.size();
        long hit1 = results.stream().filter(r -> hitAt(r.rank(), 1)).count();
        long hit3 = results.stream().filter(r -> hitAt(r.rank(), 3)).count();
        double mrr = results.stream().mapToDouble(r -> reciprocal(r.rank())).average().orElse(0);
        List<Long> ms = results.stream().map(CaseResult::millis).sorted().toList();

        // hit@3 per kind: where does each method win or lose?
        Map<String, String> hit3ByKind = new LinkedHashMap<>();
        Map<String, int[]> counts = new LinkedHashMap<>();   // kind -> [hits, total]
        for (int i = 0; i < n; i++) {
            int[] c = counts.computeIfAbsent(questions.get(i).kind(), kind -> new int[2]);
            c[0] += hitAt(results.get(i).rank(), 3) ? 1 : 0;
            c[1]++;
        }
        counts.forEach((kind, c) -> hit3ByKind.put(kind, c[0] + "/" + c[1]));

        Map<String, Object> s = new LinkedHashMap<>();
        s.put("hit@1", hit1 + "/" + n);
        s.put("hit@3", hit3 + "/" + n);
        s.put("mrr", Math.round(mrr * 1000) / 1000.0);
        s.put("medianMs", ms.isEmpty() ? 0 : ms.get(ms.size() / 2));
        s.put("maxMs", ms.isEmpty() ? 0 : ms.getLast());
        s.put("hit@3ByKind", hit3ByKind);
        return s;
    }

    /** The comparison table, ready to paste into the lesson page. */
    @SuppressWarnings("unchecked")
    private static String markdown(Map<String, Object> summary) {
        StringBuilder sb = new StringBuilder("| Method | hit@1 | hit@3 | MRR | median ms | max ms | hit@3 by kind |\n");
        sb.append("|---|---|---|---|---|---|---|\n");
        summary.forEach((method, value) -> {
            Map<String, Object> s = (Map<String, Object>) value;
            sb.append("| ").append(method)
                    .append(" | ").append(s.get("hit@1"))
                    .append(" | ").append(s.get("hit@3"))
                    .append(" | ").append(s.get("mrr"))
                    .append(" | ").append(s.get("medianMs"))
                    .append(" | ").append(s.get("maxMs"))
                    .append(" | ").append(s.get("hit@3ByKind"))
                    .append(" |\n");
        });
        return sb.toString();
    }
}
