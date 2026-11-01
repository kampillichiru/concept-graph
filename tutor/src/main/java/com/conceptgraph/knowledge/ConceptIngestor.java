package com.conceptgraph.knowledge;

import com.conceptgraph.retrieval.Embeddings;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

/**
 * The ingestion pipeline (POC 3.1): notes -> chunks -> vectors -> Qdrant.
 * Idempotent: a chunk always gets the same point id, so running ingest again overwrites instead of duplicating.
 */
@Component
public class ConceptIngestor {

    /** The collection the tutor answers from: section chunks. */
    public static final String COLLECTION = ChunkStrategy.SECTION.collection();

    private static final Logger log = LoggerFactory.getLogger(ConceptIngestor.class);

    public record IngestReport(String strategy, String collection, int notes, int chunks, long millis) {
    }

    private final NoteLoader loader;
    private final EmbeddingModel embeddingModel;
    private final QdrantClient qdrant;

    public ConceptIngestor(NoteLoader loader, EmbeddingModel embeddingModel, QdrantClient qdrant) {
        this.loader = loader;
        this.embeddingModel = embeddingModel;
        this.qdrant = qdrant;
    }

    /** Preview only: what the section chunker produces. No embeddings, no Qdrant. */
    public List<Chunk> chunks() {
        return chunks(ChunkStrategy.SECTION);
    }

    public List<Chunk> chunks(ChunkStrategy strategy) {
        return chunksOf(loader.loadAll(), strategy);
    }

    /** The store for one strategy's collection. Used by ingestion and by the comparison. */
    public EmbeddingStore<TextSegment> storeFor(ChunkStrategy strategy) {
        return QdrantEmbeddingStore.builder()
                .client(qdrant)
                .collectionName(strategy.collection())
                .build();
    }

    public IngestReport ingest() {
        return ingest(ChunkStrategy.SECTION);
    }

    public IngestReport ingest(ChunkStrategy strategy) {
        long start = System.nanoTime();
        List<ConceptNote> notes = loader.loadAll();
        List<Chunk> chunks = chunksOf(notes, strategy);

        // What we EMBED: the title (so "RAG" is in every chunk's meaning) plus the text, with the document prefix.
        List<TextSegment> toEmbed = chunks.stream()
                .map(c -> TextSegment.from(Embeddings.forDocument(c.title() + "\n" + c.text())))
                .toList();
        List<Embedding> vectors = embeddingModel.embedAll(toEmbed).content();
        ensureCollection(strategy.collection(), vectors.get(0).dimension());

        // What we STORE: the plain text plus metadata, so answers can cite conceptId and section.
        List<TextSegment> stored = chunks.stream().map(ConceptIngestor::segment).toList();
        List<String> pointIds = chunks.stream().map(c -> pointId(c.id())).toList();
        storeFor(strategy).addAll(pointIds, vectors, stored);

        long millis = (System.nanoTime() - start) / 1_000_000;
        log.info("Ingested {} notes as {} {} chunks into '{}' in {} ms",
                notes.size(), chunks.size(), strategy, strategy.collection(), millis);
        return new IngestReport(strategy.name(), strategy.collection(), notes.size(), chunks.size(), millis);
    }

    /**
     * Creates the collection on first run, sized to the embedding model's output (768 for nomic) and using
     * cosine distance. Qdrant rejects upserts into a missing collection, so this must run before the first write.
     */
    private void ensureCollection(String collection, int dimension) {
        try {
            if (qdrant.collectionExistsAsync(collection).get()) {
                return;
            }
            VectorParams params = VectorParams.newBuilder()
                    .setSize(dimension)
                    .setDistance(Distance.Cosine)
                    .build();
            qdrant.createCollectionAsync(collection, params).get();
            log.info("Created Qdrant collection '{}' ({} dimensions, cosine)", collection, dimension);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while checking Qdrant collection", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Could not create Qdrant collection " + collection, e);
        }
    }

    private static List<Chunk> chunksOf(List<ConceptNote> notes, ChunkStrategy strategy) {
        return notes.stream()
                .flatMap(n -> switch (strategy) {
                    case SECTION -> SectionChunker.chunk(n).stream();
                    case FIXED -> FixedSizeChunker.chunk(n, 250, 50).stream();
                })
                .toList();
    }

    /**
     * Qdrant point ids must be UUIDs (or integers). Deriving a UUID from the readable chunk id
     * gives the same UUID every run: that is what makes ingestion idempotent.
     */
    static String pointId(String chunkId) {
        return UUID.nameUUIDFromBytes(chunkId.getBytes(StandardCharsets.UTF_8)).toString();
    }

    static TextSegment segment(Chunk c) {
        return TextSegment.from(c.text(), Metadata.from(Map.of(
                "chunkId", c.id(),
                "conceptId", c.conceptId(),
                "title", c.title(),
                "category", c.category(),
                "level", String.valueOf(c.level()),
                "section", c.section())));
    }
}
