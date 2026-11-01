package com.conceptgraph.lessons.poc01;

import com.conceptgraph.tutor.ConceptCard;
import com.conceptgraph.tutor.ConceptExplainer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * POC 1, lesson 3: AI Service + structured output.
 * GET /poc01/concept-card?concept=embeddings
 *
 * The LLM answers in JSON matching the ConceptCard schema, LangChain4j parses it into the
 * record, and Spring turns the record back into JSON for the HTTP response.
 */
@Tag(name = "POC 1.3 Structured output", description = "AI Service returning a typed Java record (ConceptCard)")
@RestController
@RequestMapping("/poc01")
public class Poc01StructuredOutputController {

    private final ConceptExplainer explainer;   // an interface; LangChain4j made the implementation

    public Poc01StructuredOutputController(ConceptExplainer explainer) {
        this.explainer = explainer;
    }

    @Operation(summary = "Explain a concept as a ConceptCard",
            description = "The LLM must answer in JSON matching the ConceptCard schema. Try a made-up concept too.")
    @GetMapping("/concept-card")
    public ConceptCard conceptCard(@RequestParam(defaultValue = "embeddings") String concept) {
        ConceptCard card = explainer.explain(concept);
        // It's a normal Java object now: you could validate it, store it, or use card.keyTerms()
        return card;
    }
}
