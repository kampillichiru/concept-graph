package com.conceptgraph.retrieval;

import com.conceptgraph.tracing.Traces;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.stereotype.Component;

import java.util.List;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * Vector search (the POC 3 path), returning the shared RetrievedChunk shape, with an optional metadata filter.
 * The filter runs inside Qdrant: only points whose payload matches are scored at all.
 */
@Component
public class VectorRetriever {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> store;
    private final Tracer tracer;

    public VectorRetriever(EmbeddingModel embeddingModel, EmbeddingStore<TextSegment> store, Tracer tracer) {
        this.embeddingModel = embeddingModel;
        this.store = store;
        this.tracer = tracer;
    }

    public List<RetrievedChunk> search(String query, int k, String category) {
        return Traces.inSpan(tracer, "retrieve.vector", span -> {
            EmbeddingSearchRequest.EmbeddingSearchRequestBuilder request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(embeddingModel.embed(Embeddings.forQuery(query)).content())
                    .maxResults(k);
            if (category != null && !category.isBlank()) {
                request.filter(metadataKey("category").isEqualTo(category));   // payload filter in Qdrant
                span.setAttribute("retrieve.filter.category", category);
            }
            List<RetrievedChunk> hits = store.search(request.build()).matches().stream()
                    .map(m -> new RetrievedChunk(
                            m.embedded().metadata().getString("chunkId"),
                            m.embedded().metadata().getString("conceptId"),
                            m.embedded().metadata().getString("section"),
                            m.embedded().text(),
                            m.score()))
                    .toList();
            span.setAttribute("retrieve.hits", hits.size());
            return hits;
        });
    }
}
