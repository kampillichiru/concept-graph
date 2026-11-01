package com.conceptgraph.lessons.poc02;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The POC 2 test sentences, embedded once and kept in two in-memory vector stores:
 *  - raw:      embedded exactly as written
 *  - prefixed: embedded with nomic's "search_document: " prefix (the recommended way)
 * Built lazily on first use, so starting the app doesn't call Ollama.
 */
@Component
public class SentenceIndex {

    /** nomic-embed-text was trained with task prefixes: one for questions, one for documents. */
    public static final String QUERY_PREFIX = "search_query: ";
    public static final String DOCUMENT_PREFIX = "search_document: ";

    private static final Logger log = LoggerFactory.getLogger(SentenceIndex.class);

    public record Sentence(String id, String text) {
    }

    private final EmbeddingModel embeddingModel;
    private final List<Sentence> sentences;

    private List<Embedding> rawEmbeddings;
    private InMemoryEmbeddingStore<TextSegment> rawStore;
    private InMemoryEmbeddingStore<TextSegment> prefixedStore;

    public SentenceIndex(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
        this.sentences = load();
    }

    public List<Sentence> sentences() {
        return sentences;
    }

    public synchronized List<Embedding> rawEmbeddings() {
        build();
        return rawEmbeddings;
    }

    public synchronized InMemoryEmbeddingStore<TextSegment> store(boolean prefixed) {
        build();
        return prefixed ? prefixedStore : rawStore;
    }

    /** Embeds a search query, with or without nomic's query prefix. */
    public Embedding embedQuery(String query, boolean prefixed) {
        return embeddingModel.embed(prefixed ? QUERY_PREFIX + query : query).content();
    }

    private void build() {
        if (rawStore != null) {
            return;
        }
        long start = System.nanoTime();
        List<TextSegment> raw = sentences.stream().map(s -> segment(s, s.text())).toList();
        List<TextSegment> prefixed = sentences.stream().map(s -> segment(s, DOCUMENT_PREFIX + s.text())).toList();

        // embedAll sends the whole batch in one request: much faster than one call per sentence
        rawEmbeddings = embeddingModel.embedAll(raw).content();
        List<Embedding> prefixedEmbeddings = embeddingModel.embedAll(prefixed).content();

        rawStore = new InMemoryEmbeddingStore<>();
        rawStore.addAll(rawEmbeddings, raw);
        prefixedStore = new InMemoryEmbeddingStore<>();
        // store the original text (without the prefix) so results read nicely
        prefixedStore.addAll(prefixedEmbeddings, raw);

        log.info("Embedded {} sentences twice (raw + prefixed) in {} ms",
                sentences.size(), (System.nanoTime() - start) / 1_000_000);
    }

    private static TextSegment segment(Sentence s, String text) {
        // metadata travels with the vector; later POCs store conceptId here
        return TextSegment.from(text, Metadata.from(Map.of("id", s.id())));
    }

    private static List<Sentence> load() {
        try {
            String content = new ClassPathResource("poc02/sentences.txt").getContentAsString(StandardCharsets.UTF_8);
            List<Sentence> result = new ArrayList<>();
            for (String line : content.split("\\R")) {
                String text = line.strip();
                if (!text.isEmpty() && !text.startsWith("#")) {
                    result.add(new Sentence("S" + (result.size() + 1), text));
                }
            }
            return List.copyOf(result);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
