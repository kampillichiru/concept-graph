package com.conceptgraph.rag;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;

/**
 * AI Service for POC 3.5. LangChain4j implements it: the RetrievalAugmentor (wired in LlmConfig)
 * fetches notes and adds them to the message before the model sees it.
 * Returning Result<String> exposes the retrieved sources, so you can see what was injected.
 */
public interface NotesAssistant {

    @SystemMessage(fromResource = "prompts/rag-system-v2.txt")
    Result<String> answer(String question);
}
