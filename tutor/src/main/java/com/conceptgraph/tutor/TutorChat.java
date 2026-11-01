package com.conceptgraph.tutor;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * Chat with memory. @MemoryId tells LangChain4j which conversation's history to load,
 * so two users never see each other's messages.
 */
public interface TutorChat {

    @SystemMessage(fromResource = "/prompts/tutor-chat-v1.txt")
    String chat(@MemoryId String conversationId, @UserMessage String message);
}
