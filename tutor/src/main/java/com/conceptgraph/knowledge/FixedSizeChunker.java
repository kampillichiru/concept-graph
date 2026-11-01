package com.conceptgraph.knowledge;

import java.util.ArrayList;
import java.util.List;

/**
 * The baseline chunker: cut the body every {@code size} characters, stepping forward by {@code size - overlap}.
 * It ignores headings and sentences on purpose, so you can see what goes wrong: a definition cut in half,
 * or a chunk that starts in the middle of a word.
 */
public final class FixedSizeChunker {

    private FixedSizeChunker() {
    }

    public static List<Chunk> chunk(ConceptNote note, int size, int overlap) {
        if (overlap >= size) {
            throw new IllegalArgumentException("overlap must be smaller than size");
        }
        String body = note.body();
        List<Chunk> chunks = new ArrayList<>();
        int step = size - overlap;
        for (int start = 0; start < body.length(); start += step) {
            int end = Math.min(body.length(), start + size);
            String text = body.substring(start, end).strip();
            if (!text.isEmpty()) {
                chunks.add(new Chunk(note.id(), note.title(), note.category(), note.level(),
                        "chars " + start + "-" + end, text));
            }
            if (end == body.length()) {
                break;
            }
        }
        return chunks;
    }
}
