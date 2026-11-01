package com.conceptgraph.knowledge;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a note body at its "## " headings. One section = one chunk.
 * Text before the first heading becomes an "Overview" chunk.
 * Why by section: a chunk then has a meaning a human can name, and the heading is a free citation label.
 * Alternative (not used): fixed-size windows of N characters, which can cut a sentence in half.
 */
public final class SectionChunker {

    private SectionChunker() {
    }

    public static List<Chunk> chunk(ConceptNote note) {
        List<Chunk> chunks = new ArrayList<>();
        String section = "Overview";
        StringBuilder text = new StringBuilder();

        for (String line : note.body().split("\\R")) {
            if (line.startsWith("## ")) {
            	System.out.println("line:"+line);
                addIfNotEmpty(chunks, note, section, text);
                section = line.substring(3).strip();
                text.setLength(0);
            }
            text.append(line).append('\n');
        }
        addIfNotEmpty(chunks, note, section, text);
        return chunks;
    }

    private static void addIfNotEmpty(List<Chunk> chunks, ConceptNote note, String section, StringBuilder text) {
        String body = text.toString().strip();
        if (!body.isEmpty()) {
            chunks.add(new Chunk(note.id(), note.title(), note.category(), note.level(), section, body));
        }
    }
}
