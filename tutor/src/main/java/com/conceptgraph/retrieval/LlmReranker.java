package com.conceptgraph.retrieval;

import com.conceptgraph.tracing.Traces;
import dev.langchain4j.model.chat.ChatModel;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM as reranker (POC 5.4), LISTWISE: one call sees all candidates and returns an order like "3,1,2".
 * The alternative, POINTWISE (one call per candidate, "score 0-10"), is more robust but costs N calls.
 * On a CPU laptop, N = 8 calls per question is too slow, so we pay with one longer prompt instead.
 * The model's reply is parsed in code; anything it forgot keeps its original order at the end.
 */
@Component
public class LlmReranker {

    public static final String PROMPT_VERSION = "rerank-v1";
    /** How many first-stage results the model sees. More = better recall, longer prompt. */
    public static final int CANDIDATES = 8;
    private static final int MAX_PASSAGE_CHARS = 300;
    private static final Pattern NUMBER = Pattern.compile("\\d+");

    /** The new order plus the evidence: what the model replied, and how we read it. */
    public record Reranked(List<RetrievedChunk> chunks, String rawReply, List<Integer> modelOrder) {
    }

    private final ChatModel chatModel;
    private final Tracer tracer;
    private final String template;

    public LlmReranker(ChatModel chatModel, Tracer tracer) {
        this.chatModel = chatModel;
        this.tracer = tracer;
        this.template = load("prompts/" + PROMPT_VERSION + ".txt");
    }

    /** Reranks the first CANDIDATES results; results after that keep their place behind them. */
    public Reranked rerank(String question, List<RetrievedChunk> results) {
        return Traces.inSpan(tracer, "rerank", span -> {
            List<RetrievedChunk> candidates = results.subList(0, Math.min(CANDIDATES, results.size()));
            span.setAttribute("rerank.prompt_version", PROMPT_VERSION);
            span.setAttribute("rerank.candidates", candidates.size());

            String prompt = template
                    .replace("{{question}}", question)
                    .replace("{{passages}}", passages(candidates));
            String reply = chatModel.chat(prompt).strip();
            List<Integer> order = parseOrder(reply, candidates.size());

            List<RetrievedChunk> reranked = new ArrayList<>();
            order.forEach(n -> reranked.add(candidates.get(n - 1)));
            reranked.addAll(results.subList(candidates.size(), results.size()));
            span.setAttribute("rerank.reply", reply);
            span.setAttribute("rerank.model_listed", order.size());
            return new Reranked(reranked, reply, order);
        });
    }

    /** "[1] Title - Section: text..." per candidate. Newlines flattened, text capped so the prompt stays short. */
    static String passages(List<RetrievedChunk> candidates) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < candidates.size(); i++) {
            RetrievedChunk c = candidates.get(i);
            String text = c.text().replaceAll("\\s+", " ");
            if (text.length() > MAX_PASSAGE_CHARS) {
                text = text.substring(0, MAX_PASSAGE_CHARS) + "...";
            }
            sb.append('[').append(i + 1).append("] ").append(c.conceptId()).append(": ").append(text).append('\n');
        }
        return sb.toString().strip();
    }

    /**
     * Reads "3, 1, 5" (or "3 > 1 > 5", or "Passage 3 is best...") as 1-based positions.
     * Out-of-range and repeated numbers are ignored; missing positions are appended in their original order,
     * so the result is always a complete permutation, even when the model's reply is garbage.
     */
    static List<Integer> parseOrder(String reply, int n) {
        Set<Integer> order = new LinkedHashSet<>();
        Matcher m = NUMBER.matcher(reply);
        while (m.find()) {
            int pos = Integer.parseInt(m.group());
            if (pos >= 1 && pos <= n) {
                order.add(pos);
            }
        }
        for (int pos = 1; pos <= n; pos++) {
            order.add(pos);   // no-op for positions already listed
        }
        return List.copyOf(order);
    }

    private static String load(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
