package com.conceptgraph.tutor;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * An AI Service: you write only the interface, LangChain4j generates the implementation.
 * Calling explain("embeddings") builds the messages, calls the model, and parses the JSON
 * answer into a ConceptCard.
 */
public interface ConceptExplainer {

    @SystemMessage(fromResource = "/prompts/concept-explainer-v1.txt")
    @UserMessage("Explain the concept: {{concept}}")
    ConceptCard explain(@V("concept") String concept);
}
