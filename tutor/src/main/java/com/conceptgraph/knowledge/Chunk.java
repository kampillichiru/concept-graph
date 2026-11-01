package com.conceptgraph.knowledge;

import java.util.Locale;

/**
 * One section of one note: the unit we embed and retrieve.
 * Small enough to fit in a prompt, big enough to answer one question.
 */
public record Chunk(
        String conceptId,
        String title,
        String category,
        int level,
        String section,
        String text) {

    /** Human-readable, stable id, for example "embeddings#why-cosine". */
    public String id() {
        return conceptId + "#" + slug(section);
    }

    static String slug(String heading) {
        return heading.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
