package com.conceptgraph.llm;

import com.conceptgraph.config.ConceptGraphProperties;
import com.conceptgraph.tracing.TracingChatModelListener;
import com.conceptgraph.tracing.TracingEmbeddingModelListener;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The ONLY class that knows we use Ollama. Everything else depends on the
 * LangChain4j interfaces {@link ChatModel}, {@link StreamingChatModel} and {@link EmbeddingModel}.
 * Switching to another provider later means changing this one class.
 *
 * Usually you inject the ready-made ChatModel bean (see LlmConfig). Lessons use this
 * factory directly when they need a different temperature or model name.
 */
@Component
public class ModelFactory {

    private final ConceptGraphProperties props;
    private final LoggingChatModelListener listener = new LoggingChatModelListener();
    private final TracingChatModelListener tracingChat;
    private final TracingEmbeddingModelListener tracingEmbedding;

    public ModelFactory(ConceptGraphProperties props, TracingChatModelListener tracingChat,
                        TracingEmbeddingModelListener tracingEmbedding) {
        this.props = props;
        this.tracingChat = tracingChat;
        this.tracingEmbedding = tracingEmbedding;
    }

    public String defaultModelName() {
        return props.chatModel();
    }

    public ChatModel chatModel(double temperature) {
        return chatModel(props.chatModel(), temperature);
    }

    public ChatModel chatModel(String modelName, double temperature) {
        return OllamaChatModel.builder()
                .baseUrl(props.ollamaBaseUrl())
                .modelName(modelName)
                .temperature(temperature)
                .timeout(props.chatTimeout())
                // Lets AI Services send a JSON schema, so Ollama constrains output to it
                .supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA)
                .logRequests(props.logRequests())
                .logResponses(props.logRequests())
                .listeners(List.of(listener, tracingChat))
                .build();
    }

    public StreamingChatModel streamingChatModel(double temperature) {
        return OllamaStreamingChatModel.builder()
                .baseUrl(props.ollamaBaseUrl())
                .modelName(props.chatModel())
                .temperature(temperature)
                .timeout(props.chatTimeout())
                .logRequests(props.logRequests())
                .logResponses(props.logRequests())
                .listeners(List.of(listener))
                .build();
    }

    /** Embedding model: text in, vector (list of numbers) out. Used from POC 2 onwards. */
    public EmbeddingModel embeddingModel() {
        return OllamaEmbeddingModel.builder()
                .baseUrl(props.ollamaBaseUrl())
                .modelName(props.embedModel())
                .timeout(props.chatTimeout())
                .logRequests(props.logRequests())
                .logResponses(props.logRequests())
                .listeners(List.of(tracingEmbedding))
                .build();
    }
}
