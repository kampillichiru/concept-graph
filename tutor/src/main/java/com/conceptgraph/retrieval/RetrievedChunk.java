package com.conceptgraph.retrieval;

/**
 * One search result, the same shape for every retriever (vector, BM25, hybrid, reranked).
 * The score is only comparable within one retriever: cosine, BM25 and RRF use different scales.
 */
public record RetrievedChunk(String chunkId, String conceptId, String section, String text, double score) {

    public RetrievedChunk withScore(double newScore) {
        return new RetrievedChunk(chunkId, conceptId, section, text, newScore);
    }
}
