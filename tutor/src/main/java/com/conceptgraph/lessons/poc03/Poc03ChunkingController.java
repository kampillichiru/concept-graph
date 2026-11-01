package com.conceptgraph.lessons.poc03;

import com.conceptgraph.config.ConceptGraphProperties;
import com.conceptgraph.knowledge.ChunkStrategy;
import com.conceptgraph.knowledge.ConceptIngestor;
import com.conceptgraph.retrieval.Embeddings;
import com.conceptgraph.retrieval.VectorMath;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * POC 3, lesson 6: measure the chunking decision instead of arguing about it.
 * GET /poc03/compare-chunking  the same labelled questions against the SECTION and FIXED collections.
 * Needs both collections: POST /poc03/ingest (SECTION) and POST /poc03/ingest?strategy=FIXED first.
 */
@RestController
@RequestMapping("/poc03")
public class Poc03ChunkingController {

    /** One labelled question: which concept should be found, and which phrase the answer depends on. */
    record LabelledQuestion(String question, String expectedConceptId, String keyword) {
    }

    private final ConceptIngestor ingestor;
    private final EmbeddingModel embeddingModel;
    private final ConceptGraphProperties props;

    public Poc03ChunkingController(ConceptIngestor ingestor, EmbeddingModel embeddingModel,
                                   ConceptGraphProperties props) {
        this.ingestor = ingestor;
        this.embeddingModel = embeddingModel;
        this.props = props;
    }

    @Tag(name = "POC 3.6 Chunking experiment", description = "Section vs fixed-size chunks, same questions")
    @Operation(summary = "Compare the two chunking strategies on labelled questions",
            description = "hit@k: the expected concept is in the top k chunks (note-level). keyword@k: a top k chunk "
                    + "contains the phrase the answer depends on (answer-level, stricter). Look at the rows: "
                    + "where does FIXED cut the phrase in half? Which strategy keeps the answer whole?")
    @GetMapping("/compare-chunking")
    public Map<String, Object> compare(@RequestParam(defaultValue = "3") int k) {
        List<LabelledQuestion> questions = loadQuestions();
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Integer> hitK = new LinkedHashMap<>();
        Map<String, Integer> keywordK = new LinkedHashMap<>();

        for (LabelledQuestion q : questions) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("question", q.question());
            row.put("expected", q.expectedConceptId());
            row.put("keyword", q.keyword());
            for (ChunkStrategy strategy : ChunkStrategy.values()) {
                List<EmbeddingMatch<TextSegment>> matches = search(strategy, q.question(), k);
                boolean conceptHit = matches.stream()
                        .anyMatch(m -> m.embedded().metadata().getString("conceptId").equals(q.expectedConceptId()));
                boolean keywordHit = matches.stream()
                        .anyMatch(m -> normalise(m.embedded().text()).contains(normalise(q.keyword())));
                hitK.merge(strategy.name(), conceptHit ? 1 : 0, Integer::sum);
                keywordK.merge(strategy.name(), keywordHit ? 1 : 0, Integer::sum);

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("hit@k", conceptHit);
                result.put("keyword@k", keywordHit);
                result.put("topChunks", matches.stream().map(Poc03ChunkingController::label).toList());
                row.put(strategy.name(), result);
            }
            rows.add(row);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("questions", questions.size());
        summary.put("k", k);
        for (ChunkStrategy strategy : ChunkStrategy.values()) {
            summary.put(strategy.name() + " hit@k", hitK.get(strategy.name()) + "/" + questions.size());
            summary.put(strategy.name() + " keyword@k", keywordK.get(strategy.name()) + "/" + questions.size());
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("summary", summary);
        response.put("rows", rows);
        return response;
    }

    /** Same query path as RagService: prefixed question, top-k search in the strategy's collection. */
    private List<EmbeddingMatch<TextSegment>> search(ChunkStrategy strategy, String question, int k) {
        Embedding queryVector = embeddingModel.embed(Embeddings.forQuery(question)).content();
        EmbeddingStore<TextSegment> store = ingestor.storeFor(strategy);
        return store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(queryVector)
                .maxResults(k)
                .build()).matches();
    }

    /** Lower case, one space between words: a phrase split across a line break still matches. */
    static String normalise(String text) {
        return text.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT).strip();
    }

    private static Map<String, Object> label(EmbeddingMatch<TextSegment> m) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("conceptId", m.embedded().metadata().getString("conceptId"));
        row.put("section", m.embedded().metadata().getString("section"));
        row.put("score", VectorMath.round(m.score(), 3));
        row.put("text", m.embedded().text());
        return row;
    }

    private List<LabelledQuestion> loadQuestions() {
        Path file = Path.of(props.knowledgeBaseDir(), "evals", "chunking-questions.txt");
        try {
            List<LabelledQuestion> result = new ArrayList<>();
            for (String line : Files.readString(file, StandardCharsets.UTF_8).split("\\R")) {
                String text = line.strip();
                if (text.isEmpty() || text.startsWith("#")) {
                    continue;
                }
                String[] parts = text.split("\\|");
                if (parts.length != 3) {
                    throw new IllegalArgumentException("Expected 'question | concept | keyword': " + text);
                }
                result.add(new LabelledQuestion(parts[0].strip(), parts[1].strip(), parts[2].strip()));
            }
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file.toAbsolutePath(), e);
        }
    }
}
