package com.conceptgraph.evals;

import com.conceptgraph.config.ConceptGraphProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads knowledge-base/evals/golden-set.txt: "question | expected concept id | kind" per line.
 * Read on every call, so you can add a question and re-run the eval without restarting.
 */
@Component
public class GoldenSet {

    public record GoldenQuestion(String question, String expectedConceptId, String kind) {
    }

    private final ConceptGraphProperties props;

    public GoldenSet(ConceptGraphProperties props) {
        this.props = props;
    }

    public List<GoldenQuestion> load() {
        Path file = Path.of(props.knowledgeBaseDir(), "evals", "golden-set.txt");
        try {
            List<GoldenQuestion> result = new ArrayList<>();
            for (String line : Files.readString(file, StandardCharsets.UTF_8).split("\\R")) {
                String text = line.strip();
                if (text.isEmpty() || text.startsWith("#")) {
                    continue;
                }
                String[] parts = text.split("\\|");
                if (parts.length != 3) {
                    throw new IllegalArgumentException("Expected 'question | concept | kind': " + text);
                }
                result.add(new GoldenQuestion(parts[0].strip(), parts[1].strip(), parts[2].strip()));
            }
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file.toAbsolutePath(), e);
        }
    }
}
