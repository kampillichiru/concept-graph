package com.conceptgraph.lessons.poc02;

import com.conceptgraph.retrieval.VectorMath;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.CosineSimilarity;
import dev.langchain4j.store.embedding.RelevanceScore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * POC 2, lesson 1: what an embedding is, and how similarity is measured.
 * GET /poc02/embed       text -> vector of numbers
 * GET /poc02/similarity  cosine similarity of two texts, computed by hand
 * GET /poc02/pairs       hand-picked pairs that show meaning vs words
 */
@Tag(name = "POC 2.1 What is an embedding", description = "Text becomes a vector; similar meaning = similar direction")
@RestController
@RequestMapping("/poc02")
public class Poc02EmbeddingController {

    private final EmbeddingModel embeddingModel;

    public Poc02EmbeddingController(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Operation(summary = "Turn a text into a vector",
            description = "Look at: dimensions (how many numbers), the first 10 numbers, the vector length, "
                    + "and the time. Compare the time with a chat call from POC 1.")
    @GetMapping("/embed")
    public Map<String, Object> embed(@RequestParam(defaultValue = "An AI agent uses tools to complete tasks.") String text) {
        long start = System.nanoTime();
        Embedding embedding = embeddingModel.embed(text).content();
        long ms = (System.nanoTime() - start) / 1_000_000;

        float[] v = embedding.vector();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("text", text);
        result.put("dimensions", embedding.dimension());
        result.put("first10", Arrays.copyOf(v, 10));
        result.put("vectorLength", VectorMath.round(VectorMath.norm(v), 4));
        result.put("millis", ms);
        result.put("bytesAsFloat32", v.length * 4);
        return result;
    }

    @Operation(summary = "Cosine similarity of two texts",
            description = "Computed by hand (VectorMath.cosine) and checked against LangChain4j. "
                    + "Also shows LangChain4j's 'relevance score' = (cosine + 1) / 2, which is what vector stores return.")
    @GetMapping("/similarity")
    public Map<String, Object> similarity(
            @RequestParam(defaultValue = "An AI agent uses tools to complete tasks.") String a,
            @RequestParam(defaultValue = "An autonomous assistant plans steps and acts on its own.") String b) {
        Embedding ea = embeddingModel.embed(a).content();
        Embedding eb = embeddingModel.embed(b).content();

        double byHand = VectorMath.cosine(ea.vector(), eb.vector());
        double byLibrary = CosineSimilarity.between(ea, eb);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("a", a);
        result.put("b", b);
        result.put("cosineByHand", VectorMath.round(byHand, 4));
        result.put("cosineLangChain4j", VectorMath.round(byLibrary, 4));
        result.put("relevanceScore", VectorMath.round(RelevanceScore.fromCosineSimilarity(byHand), 4));
        return result;
    }

    record Pair(String why, String a, String b) {
    }

    private static final List<Pair> PAIRS = List.of(
            new Pair("synonyms, no shared words",
                    "An AI agent uses tools to complete tasks.", "An autonomous assistant plans steps and acts on its own."),
            new Pair("same topic, different words",
                    "Embeddings turn text into vectors of numbers.", "A vector database stores embeddings and finds the nearest ones."),
            new Pair("'Java' language vs Spring (related tech)",
                    "Java is a popular programming language for backend services.", "Spring Boot makes it easy to build Java microservices."),
            new Pair("'Java' language vs 'Java' island (shared word, different meaning)",
                    "Java is a popular programming language for backend services.", "Java is an island in Indonesia with many volcanoes."),
            new Pair("'java' drink vs coffee (no shared words, same meaning)",
                    "I drink a cup of java every morning.", "Coffee beans are roasted before brewing."),
            new Pair("island vs island",
                    "Java is an island in Indonesia with many volcanoes.", "Bali is a beautiful island for a beach holiday."),
            new Pair("unrelated",
                    "An AI agent uses tools to complete tasks.", "How to cook pasta with tomato sauce."));

    @Operation(summary = "Hand-picked pairs: meaning vs shared words",
            description = "Sorted from most to least similar. Which pairs with NO shared words score high? "
                    + "Which pairs WITH a shared word ('Java') score low? Note how narrow the range is.")
    @GetMapping("/pairs")
    public List<Map<String, Object>> pairs() {
        return PAIRS.stream()
                .map(p -> {
                    double cos = CosineSimilarity.between(embeddingModel.embed(p.a()).content(), embeddingModel.embed(p.b()).content());
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("cosine", VectorMath.round(cos, 3));
                    row.put("why", p.why());
                    row.put("a", p.a());
                    row.put("b", p.b());
                    return row;
                })
                .sorted(Comparator.comparing((Map<String, Object> r) -> (Double) r.get("cosine")).reversed())
                .toList();
    }
}
