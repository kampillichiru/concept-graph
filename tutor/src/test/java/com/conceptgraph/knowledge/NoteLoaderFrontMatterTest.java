package com.conceptgraph.knowledge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NoteLoaderFrontMatterTest {

    @Test
    void readsEveryGraphKeyIncludingAlternativeToAndPartOf() {
        String note = """
                ---
                id: graph-rag
                title: GraphRAG
                category: Graph
                level: 3
                requires: [rag, knowledge-graph]
                related: [text-to-cypher]
                alternative_to: [rag]
                part_of: [retrieval]
                ---
                ## What it is
                Body text.
                """;
        ConceptNote parsed = NoteLoader.parse(note, "graph-rag.md");
        assertEquals("graph-rag", parsed.id());
        assertEquals(List.of("rag", "knowledge-graph"), parsed.requires());
        assertEquals(List.of("text-to-cypher"), parsed.related());
        assertEquals(List.of("rag"), parsed.alternativeTo());
        assertEquals(List.of("retrieval"), parsed.partOf());
    }

    @Test
    void missingGraphKeysAreEmptyNotNull() {
        ConceptNote parsed = NoteLoader.parse("---\nid: llm\n---\nBody.", "llm.md");
        assertEquals(List.of(), parsed.alternativeTo());
        assertEquals(List.of(), parsed.partOf());
    }
}
