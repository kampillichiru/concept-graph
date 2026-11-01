package com.conceptgraph.lessons.poc02;

import com.conceptgraph.config.ConceptGraphProperties;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * POC 2 architect artifact input: numbers for "how to choose an embedding model".
 * GET /poc02/embedding-stats
 */
@Tag(name = "POC 2.5 Architect artifact", description = "Speed and storage numbers for choosing an embedding model")
@RestController
@RequestMapping("/poc02")
public class Poc02StatsController {

    private final EmbeddingModel embeddingModel;
    private final SentenceIndex index;
    private final ConceptGraphProperties props;

    public Poc02StatsController(EmbeddingModel embeddingModel, SentenceIndex index, ConceptGraphProperties props) {
        this.embeddingModel = embeddingModel;
        this.index = index;
        this.props = props;
    }

    @Operation(summary = "Measure the embedding model",
            description = "Latency per text, batch vs one-by-one, vector size, and storage at scale. "
                    + "Used in docs/artifacts/poc02-choosing-embedding-model.md.")
    @GetMapping("/embedding-stats")
    public Map<String, Object> stats() {
        List<TextSegment> segments = index.sentences().stream().map(s -> TextSegment.from(s.text())).toList();

        long t0 = System.nanoTime();
        Embedding first = embeddingModel.embed("warm-up").content();   // may include model loading (cold start)
        long firstMs = ms(t0);

        long t1 = System.nanoTime();
        for (TextSegment s : segments) {
            embeddingModel.embed(s.text());
        }
        long oneByOneMs = ms(t1);

        long t2 = System.nanoTime();
        embeddingModel.embedAll(segments);
        long batchMs = ms(t2);

        int dims = first.dimension();
        long bytesPerVector = dims * 4L;   // float32
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("model", props.embedModel());
        result.put("dimensions", dims);
        result.put("firstCallMs (may include cold start)", firstMs);
        result.put("texts", segments.size());
        result.put("oneByOneTotalMs", oneByOneMs);
        result.put("oneByOneAvgMs", oneByOneMs / segments.size());
        result.put("batchTotalMs", batchMs);
        result.put("bytesPerVector", bytesPerVector);
        result.put("storageFor100kVectorsMB", bytesPerVector * 100_000 / 1_000_000);
        result.put("storageFor1MVectorsMB", bytesPerVector * 1_000_000 / 1_000_000);
        return result;
    }

    private static long ms(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
