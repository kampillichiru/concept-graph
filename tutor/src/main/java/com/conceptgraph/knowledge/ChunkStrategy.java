package com.conceptgraph.knowledge;

/**
 * The two chunking strategies we compare in POC 3 (see docs/artifacts/poc03-chunking-strategy.md).
 * Each strategy gets its own Qdrant collection, so both can be queried side by side.
 */
public enum ChunkStrategy {
    /** One chunk per "## " section. The one the tutor uses. */
    SECTION("ai_concepts"),
    /** Windows of 250 characters with 50 overlap, ignoring headings. The baseline. */
    FIXED("ai_concepts_fixed");

    private final String collection;

    ChunkStrategy(String collection) {
        this.collection = collection;
    }

    public String collection() {
        return collection;
    }
}
