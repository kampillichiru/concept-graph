package com.conceptgraph.rag;

import com.conceptgraph.retrieval.Embeddings;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * RAG written by hand (POC 3.3): embed the question, fetch the top-k chunks, put them into a numbered prompt,
 * ask the chat model. Every step is a visible method call, so you can see what LangChain4j does for you in 3.5.
 */
@Service
public class RagService {

    /** One chunk the answer was based on. */
    public record Source(String conceptId, String section, double score, String text) {
    }

    /**
     * The answer plus the evidence: the chunks, the exact prompt, and a code check of the citations.
     * unknownCitations = ids the model cited that were NOT retrieved. Any entry there is a grounding failure.
     */
    public record RagAnswer(String question, String answer, List<Source> sources,
                            List<String> citedIds, List<String> unknownCitations, String prompt) {
    }

    /** Any [bracketed] text counts as a citation attempt, so a wrong-looking one is caught too. */
    private static final Pattern CITATION = Pattern.compile("\\[([^\\[\\]\\n]+)]");

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> store;
    private final ChatModel chatModel;
    private final String template;

    public RagService(EmbeddingModel embeddingModel, EmbeddingStore<TextSegment> store, ChatModel chatModel) {
        this.embeddingModel = embeddingModel;
        this.store = store;
        this.chatModel = chatModel;
        this.template = load("prompts/rag-answer-v2.txt");
    }

    /** Step 1-2: question -> vector -> k nearest chunks. */
    public List<EmbeddingMatch<TextSegment>> retrieve(String question, int k) {
        Embedding queryVector = embeddingModel.embed(Embeddings.forQuery(question)).content();
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryVector)
                .maxResults(k)
                .build();
        return store.search(request).matches();
    }

    /** Steps 3-4: build the prompt from the chunks, then ask the model. */
    public RagAnswer answer(String question, int k) {
        List<EmbeddingMatch<TextSegment>> matches = retrieve(question, k);
        String prompt = template
                .replace("{{context}}", formatContext(matches))
                .replace("{{question}}", question);
        String answer = chatModel.chat(prompt);

        List<Source> sources = matches.stream()
                .map(m -> new Source(
                        m.embedded().metadata().getString("conceptId"),
                        m.embedded().metadata().getString("section"),
                        m.score(),
                        m.embedded().text()))
                .toList();

        // Grounding check in code: every [id] in the answer must be one of the retrieved concepts.
        Set<String> retrieved = sources.stream().map(Source::conceptId).collect(Collectors.toSet());
        List<String> cited = citations(answer);
        List<String> unknown = cited.stream().filter(id -> !retrieved.contains(id)).distinct().toList();
        return new RagAnswer(question, answer, sources, cited.stream().distinct().toList(), unknown, prompt);
    }

    static List<String> citations(String answer) {
        List<String> ids = new ArrayList<>();
        Matcher m = CITATION.matcher(answer);
        while (m.find()) {
            ids.add(m.group(1));
        }
        return ids;
    }

    /** Numbered-by-concept context: "[embeddings] (section: Why cosine)" then the text. */
    static String formatContext(List<EmbeddingMatch<TextSegment>> matches) {
        return matches.stream()
                .map(m -> "[" + m.embedded().metadata().getString("conceptId") + "] (section: "
                        + m.embedded().metadata().getString("section") + ")\n"
                        + m.embedded().text())
                .collect(Collectors.joining("\n\n"));
    }

    private static String load(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
