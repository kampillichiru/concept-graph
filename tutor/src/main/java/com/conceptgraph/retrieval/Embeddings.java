package com.conceptgraph.retrieval;

/**
 * nomic-embed-text was trained with task prefixes (see POC 2, lesson 2.4).
 * Documents get "search_document: ", questions get "search_query: ". Every RAG path goes through here,
 * so the rule lives in one place.
 */
public final class Embeddings {

    public static final String QUERY_PREFIX = "search_query: ";
    public static final String DOCUMENT_PREFIX = "search_document: ";

    private Embeddings() {
    }

    public static String forQuery(String question) {
        return QUERY_PREFIX + question;
    }

    public static String forDocument(String text) {
        return DOCUMENT_PREFIX + text;
    }
}
