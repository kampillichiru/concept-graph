package com.conceptgraph.lessons.poc02;

import com.conceptgraph.lessons.poc02.SentenceIndex.Sentence;
import com.conceptgraph.retrieval.VectorMath;
import dev.langchain4j.data.embedding.Embedding;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * POC 2, lesson 2: every sentence against every other sentence.
 * GET /poc02/matrix   plain-text table + most/least similar pairs + score range
 */
@Tag(name = "POC 2.2 Similarity matrix", description = "All test sentences compared with each other")
@RestController
@RequestMapping("/poc02")
public class Poc02MatrixController {

    private final SentenceIndex index;

    public Poc02MatrixController(SentenceIndex index) {
        this.index = index;
    }

    record ScoredPair(Sentence a, Sentence b, double cosine) {
    }

    @Operation(summary = "Similarity matrix of the 15 test sentences",
            description = "Read the 'score range' at the bottom: even unrelated sentences rarely score near 0. "
                    + "That's why a fixed threshold like 0.5 means nothing on its own; only the ranking matters.")
    @GetMapping(value = "/matrix", produces = MediaType.TEXT_PLAIN_VALUE)
    public String matrix() {
        List<Sentence> sentences = index.sentences();
        List<Embedding> embeddings = index.rawEmbeddings();
        int n = sentences.size();

        double[][] m = new double[n][n];
        List<ScoredPair> pairs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                m[i][j] = VectorMath.cosine(embeddings.get(i).vector(), embeddings.get(j).vector());
                if (j > i) {
                    pairs.add(new ScoredPair(sentences.get(i), sentences.get(j), m[i][j]));
                }
            }
        }

        StringBuilder out = new StringBuilder("Cosine similarity (1.00 = identical)\n\n     ");
        for (int j = 0; j < n; j++) {
            out.append(String.format("%5s", "S" + (j + 1)));
        }
        out.append('\n');
        for (int i = 0; i < n; i++) {
            out.append(String.format("%-5s", "S" + (i + 1)));
            for (int j = 0; j < n; j++) {
                out.append(String.format("%5.2f", m[i][j]));
            }
            out.append('\n');
        }

        out.append("\nSentences\n");
        sentences.forEach(s -> out.append(String.format("  %-4s %s%n", s.id(), s.text())));

        pairs.sort(Comparator.comparingDouble(ScoredPair::cosine).reversed());
        out.append("\nMost similar pairs\n");
        pairs.stream().limit(5).forEach(p -> appendPair(out, p));
        out.append("\nLeast similar pairs\n");
        pairs.subList(pairs.size() - 5, pairs.size()).forEach(p -> appendPair(out, p));

        double min = pairs.getLast().cosine();
        double max = pairs.getFirst().cosine();
        double mean = pairs.stream().mapToDouble(ScoredPair::cosine).average().orElse(0);
        out.append(String.format("%nScore range (excluding self-matches): min %.2f, mean %.2f, max %.2f%n", min, mean, max));
        return out.toString();
    }

    private static void appendPair(StringBuilder out, ScoredPair p) {
        out.append(String.format("  %.3f  %-4s %s%n         %-4s %s%n", p.cosine(), p.a().id(), p.a().text(), p.b().id(), p.b().text()));
    }
}
