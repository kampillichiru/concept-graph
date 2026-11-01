package com.conceptgraph.lessons.poc03;

import com.conceptgraph.rag.RagService;
import com.conceptgraph.rag.RagService.RagAnswer;
import dev.langchain4j.model.chat.ChatModel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * POC 3, lessons 3 and 4: RAG by hand, and the same question without RAG.
 * GET /poc03/ask             retrieve -> numbered prompt -> answer with citations
 * GET /poc03/ask-without-rag the same question, no notes: compare the answers
 */
@RestController
@RequestMapping("/poc03")
public class Poc03AskController {

    private final RagService rag;
    private final ChatModel chatModel;

    public Poc03AskController(RagService rag, ChatModel chatModel) {
        this.rag = rag;
        this.chatModel = chatModel;
    }

    @Tag(name = "POC 3.3 RAG by hand", description = "Retrieve, augment the prompt, answer with citations")
    @Operation(summary = "Answer from the notes, with citations",
            description = "Read 'prompt' in the response: that exact text is what the model saw. "
                    + "Ask something in the notes, then something not in them. Does it cite [concept-id], "
                    + "or does it say 'I don't know from my notes'?")
    @GetMapping("/ask")
    public RagAnswer ask(@RequestParam(defaultValue = "Why do we split notes into chunks?") String question,
                         @RequestParam(defaultValue = "3") int k) {
        return rag.answer(question, k);
    }

    @Tag(name = "POC 3.4 Break it: no RAG", description = "The same question with no notes")
    @Operation(summary = "Same question, no retrieval",
            description = "Compare with /poc03/ask. Without the notes the model answers from its training data, "
                    + "with no citations and no way to check the claims. Is the answer still correct for this question?")
    @GetMapping("/ask-without-rag")
    public Map<String, Object> askWithoutRag(@RequestParam(defaultValue = "Why do we split notes into chunks?") String question) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("question", question);
        result.put("answer", chatModel.chat(question));
        return result;
    }
}
