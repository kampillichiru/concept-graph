package com.conceptgraph.retrieval;

import com.conceptgraph.knowledge.ConceptIngestor;
import com.conceptgraph.tracing.Traces;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * BM25 over the same section chunks that are in Qdrant (POC 5.1).
 * The index lives in memory and is built on first use: 81 chunks take a few milliseconds.
 * Chunk ids match Qdrant's chunkId payload, which is what lets RRF merge the two lists.
 */
@Component
public class KeywordRetriever {

    private final ConceptIngestor ingestor;
    private final Tracer tracer;
    private volatile Bm25Index index;

    public KeywordRetriever(ConceptIngestor ingestor, Tracer tracer) {
        this.ingestor = ingestor;
        this.tracer = tracer;
    }

    public Bm25Index index() {
        Bm25Index current = index;
        if (current == null) {
            synchronized (this) {
                if (index == null) {
                    index = new Bm25Index(ingestor.chunks());
                }
                current = index;
            }
        }
        return current;
    }

    /** Re-reads the notes. Call after editing a note. */
    public synchronized int rebuild() {
        index = new Bm25Index(ingestor.chunks());
        return index.size();
    }

    public List<Bm25Index.Hit> searchWithTerms(String query, int k, String category) {
        return index().search(query, k, c -> category == null || category.isBlank()
                || c.category().equalsIgnoreCase(category));
    }

    public List<RetrievedChunk> search(String query, int k, String category) {
        return Traces.inSpan(tracer, "retrieve.bm25", span -> {
            List<RetrievedChunk> hits = searchWithTerms(query, k, category).stream()
                    .map(h -> new RetrievedChunk(h.chunk().id(), h.chunk().conceptId(), h.chunk().section(),
                            h.chunk().text(), h.score()))
                    .toList();
            span.setAttribute("retrieve.hits", hits.size());
            return hits;
        });
    }
}
