package com.conceptgraph.llm;

import com.conceptgraph.config.ConceptGraphProperties;
import com.conceptgraph.knowledge.ConceptIngestor;
import com.conceptgraph.rag.NotesAssistant;
import com.conceptgraph.tutor.ConceptExplainer;
import com.conceptgraph.tutor.StatelessTutorChat;
import com.conceptgraph.tutor.TutorChat;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Wires LangChain4j objects as Spring beans, explicitly, so you can see every piece.
 * (LangChain4j also has a Spring Boot starter with @AiService that does this automatically;
 * we write it by hand while learning.)
 */
@Configuration
public class LlmConfig {

    /** The default model: temperature 0 for factual, repeatable answers. */
    @Bean
    ChatModel chatModel(ModelFactory factory) {
        return factory.chatModel(0.0);
    }

    @Bean
    StreamingChatModel streamingChatModel(ModelFactory factory) {
        return factory.streamingChatModel(0.0);
    }

    /** Text to vector (POC 2+). Fast and cheap compared with the chat model. */
    @Bean
    EmbeddingModel embeddingModel(ModelFactory factory) {
        return factory.embeddingModel();
    }

    /** AI Service: LangChain4j generates the implementation of the interface. */
    @Bean
    ConceptExplainer conceptExplainer(ChatModel chatModel) {
        return AiServices.create(ConceptExplainer.class, chatModel);
    }

    /** Where chat histories live. In memory here; a database in production. */
    @Bean
    ChatMemoryStore chatMemoryStore() {
        return new InMemoryChatMemoryStore();
    }

    /**
     * Chat WITH memory. A web app has many users, so memory is per conversation id:
     * the provider creates (or finds) one 10-message window for each id.
     */
    @Bean
    TutorChat tutorChat(ChatModel chatModel, ChatMemoryStore store) {
        return AiServices.builder(TutorChat.class)
                .chatModel(chatModel)
                .chatMemoryProvider(conversationId -> MessageWindowChatMemory.builder()
                        .id(conversationId)
                        .maxMessages(10)
                        .chatMemoryStore(store)
                        .build())
                .build();
    }

    /** Chat WITHOUT memory: every call starts from zero. */
    @Bean
    StatelessTutorChat statelessTutorChat(ChatModel chatModel) {
        return AiServices.create(StatelessTutorChat.class, chatModel);
    }

    /**
     * The vector store (POC 3+). One collection holds every chunk of every note.
     * The builder connects to Qdrant over gRPC; the collection name is shared with the ingestor.
     */
    @Bean
    EmbeddingStore<TextSegment> conceptStore(QdrantClient qdrantClient) {
        return QdrantEmbeddingStore.builder()
                .client(qdrantClient)
                .collectionName(ConceptIngestor.COLLECTION)
                .build();
    }

    /**
     * The low-level Qdrant client. The store uses it for search and upserts; the ingestor uses it to
     * create the collection, because the store does not create it for you.
     */
    @Bean
    QdrantClient qdrantClient(ConceptGraphProperties props) {
        return new QdrantClient(QdrantGrpcClient.newBuilder(props.qdrantHost(), props.qdrantPort(), false).build());
    }

    /**
     * POC 3.5: the same RAG idea as lessons 3.1-3.3, but assembled by LangChain4j.
     * A RetrievalAugmentor = a ContentRetriever (search the store) + a ContentInjector (put the hits into the prompt).
     */
    @Bean
    NotesAssistant notesAssistant(ChatModel chatModel, EmbeddingModel embeddingModel,
                                  EmbeddingStore<TextSegment> store) {
        ContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(store)
                .embeddingModel(embeddingModel)
                .maxResults(3)
                .build();
        RetrievalAugmentor augmentor = DefaultRetrievalAugmentor.builder()
                .contentRetriever(retriever)
                .contentInjector(DefaultContentInjector.builder()
                        .metadataKeysToInclude(List.of("conceptId", "section"))
                        .build())
                .build();
        return AiServices.builder(NotesAssistant.class)
                .chatModel(chatModel)
                .retrievalAugmentor(augmentor)
                .build();
    }
}
