package com.conceptgraph.knowledge;

import com.conceptgraph.config.ConceptGraphProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Reads the concept notes from disk and splits each file into front matter and body.
 * A note looks like:
 * <pre>
 * ---
 * id: rag
 * title: RAG
 * requires: [embeddings, vector-database]
 * ---
 * ## What it is
 * ...
 * </pre>
 * Hand-written parser on purpose: the format is small, and you can see every step.
 */
@Component
public class NoteLoader {

    private final ConceptGraphProperties props;

    public NoteLoader(ConceptGraphProperties props) {
        this.props = props;
    }

    public List<ConceptNote> loadAll() {
        Path dir = Path.of(props.knowledgeBaseDir(), "concepts");
        if (!Files.isDirectory(dir)) {
            throw new IllegalStateException("Notes folder not found: " + dir.toAbsolutePath()
                    + " (start the app from the repo root, or set KNOWLEDGE_BASE_DIR)");
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".md"))
                    .sorted()
                    .map(NoteLoader::read)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ConceptNote read(Path file) {
        try {
            return parse(Files.readString(file), file.getFileName().toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Splits "---" front matter from the body. Package-private so a test could call it directly. */
    static ConceptNote parse(String text, String fileName) {
        String[] lines = text.split("\\R");
        if (lines.length == 0 || !lines[0].strip().equals("---")) {
            throw new IllegalArgumentException(fileName + ": missing front matter (must start with ---)");
        }
        int end = 1;
        while (end < lines.length && !lines[end].strip().equals("---")) {
            end++;
        }
        if (end == lines.length) {
            throw new IllegalArgumentException(fileName + ": front matter is not closed with ---");
        }

        // "key: value" per line; keep the first colon so values may contain colons
        Map<String, String> meta = new HashMap<>();
        for (int i = 1; i < end; i++) {
            int colon = lines[i].indexOf(':');
            if (colon > 0) {
                meta.put(lines[i].substring(0, colon).strip(), lines[i].substring(colon + 1).strip());
            }
        }
        String id = meta.get("id");
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(fileName + ": front matter needs an 'id'");
        }
        String body = String.join("\n", Arrays.copyOfRange(lines, end + 1, lines.length)).strip();

        return new ConceptNote(
                id,
                meta.getOrDefault("title", id),
                meta.getOrDefault("category", ""),
                Integer.parseInt(meta.getOrDefault("level", "1")),
                parseList(meta.get("requires")),
                parseList(meta.get("related")),
                body);
    }

    /** "[a, b, c]" -> [a, b, c]; missing or "[]" -> empty list. */
    static List<String> parseList(String value) {
        if (value == null) {
            return List.of();
        }
        String inner = value.strip();
        if (inner.startsWith("[")) {
            inner = inner.substring(1);
        }
        if (inner.endsWith("]")) {
            inner = inner.substring(0, inner.length() - 1);
        }
        return Arrays.stream(inner.split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
