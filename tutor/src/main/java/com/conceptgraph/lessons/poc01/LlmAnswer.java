package com.conceptgraph.lessons.poc01;

import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

/** What lesson endpoints return: the answer plus the numbers worth observing. */
public record LlmAnswer(
        String label,
        String answer,
        Integer inputTokens,
        Integer outputTokens,
        long millis,
        double tokensPerSecond) {

    public static LlmAnswer of(String label, ChatResponse response, long millis) {
        TokenUsage u = response.tokenUsage();
        Integer in = u == null ? null : u.inputTokenCount();
        Integer out = u == null ? null : u.outputTokenCount();
        double tps = (out == null || millis == 0) ? 0 : Math.round(out * 10000.0 / millis) / 10.0;
        return new LlmAnswer(label, response.aiMessage().text().strip(), in, out, millis, tps);
    }
}
