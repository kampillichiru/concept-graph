package com.conceptgraph.tutor;

import dev.langchain4j.service.SystemMessage;

/** The same tutor, but with no memory: each call only sees the current message. */
public interface StatelessTutorChat {

    @SystemMessage(fromResource = "/prompts/tutor-chat-v1.txt")
    String chat(String message);
}
