package com.conceptgraph.lessons.poc03;

import com.conceptgraph.rag.NotesAssistant;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * POC 3, lesson 5: the same RAG, assembled by LangChain4j (RetrievalAugmentor + AI Service).
 * GET /poc03/ask-augmented  compare the answer and the sources with /poc03/ask
 */
@RestController
@RequestMapping("/poc03")
public class Poc03LangChainController {

    private final NotesAssistant assistant;

    public Poc03LangChainController(NotesAssistant assistant) {
        this.assistant = assistant;
    }

    @Tag(name = "POC 3.5 RetrievalAugmentor", description = "LangChain4j does retrieval and prompt injection")
    @Operation(summary = "RAG with LangChain4j's RetrievalAugmentor",
            description = "Same questions as /poc03/ask. Compare: the sources, the answer, and the citations. "
                    + "Note: this retriever embeds the question WITHOUT the 'search_query: ' prefix, "
                    + "while /poc03/ask adds it. Do the sources differ?")
    @GetMapping("/ask-augmented")
    public Map<String, Object> askAugmented(@RequestParam(defaultValue = "Why do we split notes into chunks?") String question) {
        Result<String> result = assistant.answer(question);

        List<Map<String, Object>> sources = result.sources().stream().map(Poc03LangChainController::source).toList();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("question", question);
        response.put("answer", result.content());
        response.put("sources", sources);
        return response;
    }

    private static Map<String, Object> source(Content content) {
        Map<String, Object> row = new LinkedHashMap<>();
        if (content.textSegment() != null) {
            row.put("conceptId", content.textSegment().metadata().getString("conceptId"));
            row.put("section", content.textSegment().metadata().getString("section"));
            row.put("text", content.textSegment().text());
        }
        return row;
    }
}
