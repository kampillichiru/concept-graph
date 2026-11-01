package com.conceptgraph.lessons.poc01;

import com.conceptgraph.llm.ModelFactory;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * POC 1, lesson 1: a raw LLM call (no AI Service yet).
 * GET /poc01/messages     system message vs user message, token counts
 * GET /poc01/temperature  temperature 0 vs 0.8
 * GET /poc01/made-up      break it: a framework that does not exist
 */
@Tag(name = "POC 1.1 Basics", description = "Raw LLM calls: system vs user message, tokens, temperature, hallucination")
@RestController
@RequestMapping("/poc01")
public class Poc01BasicsController {

    private final ChatModel chatModel;      // the default bean, temperature 0
    private final ModelFactory models;      // to build models with other temperatures

    public Poc01BasicsController(ChatModel chatModel, ModelFactory models) {
        this.chatModel = chatModel;
        this.models = models;
    }

    /** Same user question, two different system messages. */
    @Operation(summary = "Same question, two system messages",
            description = "Compare the two answers and their token counts. Only the system message differs.")
    @GetMapping("/messages")
    public List<LlmAnswer> messages(
            @RequestParam(defaultValue = "What is a context window in an LLM?") String question) {
        return List.of(
                ask(chatModel, "concise tutor",
                        "You are a concise tutor. Answer in at most 2 sentences.", question),
                ask(chatModel, "explain to a child",
                        "You explain things to a 10-year-old using one analogy. At most 2 sentences.", question));
    }

    /** Same question twice at temperature 0, twice at 0.8. */
    @Operation(summary = "Temperature 0.0 vs 0.8, each asked twice",
            description = "Do the two 0.8 runs differ more than the two 0.0 runs? Takes ~20 s.")
    @GetMapping("/temperature")
    public List<LlmAnswer> temperature(
            @RequestParam(defaultValue = "Give a one-sentence analogy for what an embedding is.") String question) {
        List<LlmAnswer> answers = new ArrayList<>();
        for (double t : new double[] {0.0, 0.8}) {
            ChatModel model = models.chatModel(t);
            for (int run = 1; run <= 2; run++) {
                answers.add(ask(model, "temperature " + t + ", run " + run, "Answer in one sentence.", question));
            }
        }
        return answers;
    }

    /** Break it: the model has no way to know this framework doesn't exist. */
    @Operation(summary = "Break it: ask about a framework that does not exist",
            description = "Zorblax is made up. Does the model admit it, or invent an answer? This is why RAG exists.")
    @GetMapping("/made-up")
    public LlmAnswer madeUp() {
        return ask(chatModel, "made-up framework", "You are a helpful Java expert. Be brief.",
                "How do I configure the QuantumBeanFactory in the Zorblax framework for Java?");
    }

    private LlmAnswer ask(ChatModel model, String label, String system, String user) {
        // A chat request is just a list of messages: system (instructions) + user (question)
        ChatRequest request = ChatRequest.builder()
                .messages(SystemMessage.from(system), UserMessage.from(user))
                .build();
        long start = System.nanoTime();
        ChatResponse response = model.chat(request);
        return LlmAnswer.of(label, response, (System.nanoTime() - start) / 1_000_000);
    }
}
