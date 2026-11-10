package com.conceptgraph.knowledge;

import java.util.List;

/**
 * One note from knowledge-base/concepts/*.md.
 * The front matter (id, title, requires, ...) describes the note; the body is the text we index.
 */
public record ConceptNote(
        String id,
        String title,
        String category,
        int level,
        List<String> requires,
        List<String> related,
        List<String> alternativeTo,
        List<String> partOf,
        String body) {
}
