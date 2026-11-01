package com.conceptgraph.tutor;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.output.structured.Description;

import java.util.List;

/**
 * Structured output: the LLM's answer is parsed straight into this record.
 * LangChain4j turns the record (and the @Description texts) into a JSON schema.
 * required = true matters: without it "{ }" is valid JSON for the schema,
 * and a small model may take that shortcut (we saw it happen).
 */
public record ConceptCard(
        @JsonProperty(required = true) @Description("the concept's name")
        String title,
        @JsonProperty(required = true) @Description("one sentence definition")
        String oneLiner,
        @JsonProperty(required = true) @Description("an everyday analogy, one or two sentences")
        String analogy,
        @JsonProperty(required = true) @Description("3 to 5 key terms")
        List<String> keyTerms) {
}
