package com.conceptgraph.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Typed settings bound from application.yml (which reads env vars like CHAT_MODEL).
 * Changing model or host is a config change, never a code change.
 */
@ConfigurationProperties("conceptgraph")
public record ConceptGraphProperties(
        String ollamaBaseUrl,
        String chatModel,
        String embedModel,
        Duration chatTimeout,
        boolean logRequests,
        String qdrantHost,
        int qdrantPort,
        String knowledgeBaseDir) {
}
