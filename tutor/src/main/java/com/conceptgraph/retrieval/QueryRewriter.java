package com.conceptgraph.retrieval;

import com.conceptgraph.tracing.Traces;
import dev.langchain4j.model.chat.ChatModel;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Query rewriting (POC 5.3): the LLM turns "that graph thing with LLMs" into "GraphRAG knowledge graph retrieval".
 * We EXPAND the query (original + keywords) instead of replacing it: if the model guesses wrong,
 * the original words are still there, so a bad rewrite costs less.
 */
@Component
public class QueryRewriter {

    /**
     * v1 listed the topics of the notes; the 3b model copied that list back as the "keywords" for every vague
     * question. v2 gives one worked example instead (few-shot). See docs/lessons/poc05.md, break-it.
     */
    public static final String PROMPT_VERSION = "query-rewrite-v2";

    private final ChatModel chatModel;
    private final Tracer tracer;
    private final Map<String, String> templates = new HashMap<>();

    public QueryRewriter(ChatModel chatModel, Tracer tracer) {
        this.chatModel = chatModel;
        this.tracer = tracer;
        for (String version : List.of("query-rewrite-v1", "query-rewrite-v2")) {
            templates.put(version, load("prompts/" + version + ".txt"));
        }
    }

    /** Only the keywords the model produced (first line, trimmed), with the current prompt. */
    public String keywords(String question) {
        return keywords(question, PROMPT_VERSION);
    }

    /** With a named prompt version, so the lesson can replay the v1 failure. */
    public String keywords(String question, String promptVersion) {
        String template = templates.get(promptVersion);
        if (template == null) {
            throw new IllegalArgumentException("Unknown prompt version " + promptVersion + ", use one of "
                    + templates.keySet());
        }
        return Traces.inSpan(tracer, "query.rewrite", span -> {
            span.setAttribute("rewrite.prompt_version", promptVersion);
            span.setAttribute("rewrite.original", question);
            String reply = chatModel.chat(template.replace("{{question}}", question)).strip();
            String keywords = reply.lines().findFirst().orElse("")
                    .replaceFirst("^(?i)(terms|keywords):", "").strip();
            if (keywords.length() > 200) {
                keywords = keywords.substring(0, 200);   // a runaway reply should not become a huge query
            }
            span.setAttribute("rewrite.keywords", keywords);
            return keywords;
        });
    }

    /** What we search with: the original question plus the keywords. */
    public String expand(String question) {
        return question + " " + keywords(question);
    }

    private static String load(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
