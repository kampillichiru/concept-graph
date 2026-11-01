package com.conceptgraph.lessons.poc01;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * POC 1, lesson 2: blocking vs streaming.
 * GET /poc01/blocking  nothing arrives until the whole answer is ready
 * GET /poc01/stream    tokens arrive one by one (use: curl.exe -N ...)
 */
@Tag(name = "POC 1.2 Streaming", description = "Blocking vs streaming: time to first token")
@RestController
@RequestMapping("/poc01")
public class Poc01StreamingController {

    private static final String DEFAULT_QUESTION = "Explain what tokens are in an LLM, in about 80 words.";

    private final ChatModel chatModel;
    private final StreamingChatModel streamingChatModel;

    public Poc01StreamingController(ChatModel chatModel, StreamingChatModel streamingChatModel) {
        this.chatModel = chatModel;
        this.streamingChatModel = streamingChatModel;
    }

    @Operation(summary = "Blocking call", description = "Nothing arrives until the whole answer is ready. Note the duration.")
    @GetMapping("/blocking")
    public LlmAnswer blocking(@RequestParam(defaultValue = DEFAULT_QUESTION) String question) {
        long start = System.nanoTime();
        ChatResponse response = chatModel.chat(ChatRequest.builder()
                .messages(UserMessage.from(question)).build());
        return LlmAnswer.of("blocking: first word = last word", response, (System.nanoTime() - start) / 1_000_000);
    }

    /**
     * Writes each token to the HTTP response as soon as the model produces it.
     * The streaming model calls our handler on another thread; we wait until it's done.
     */
    @Operation(summary = "Streaming call (tokens as they arrive)",
            description = "Swagger UI waits for the full response, so you will NOT see tokens appear here. "
                    + "Read the [first token after ... ms] line at the end, or run: curl.exe -N http://localhost:8080/poc01/stream")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<StreamingResponseBody> stream(@RequestParam(defaultValue = DEFAULT_QUESTION) String question) {
        StreamingResponseBody body = out -> {
            CompletableFuture<ChatResponse> done = new CompletableFuture<>();
            AtomicLong firstToken = new AtomicLong();
            long start = System.nanoTime();

            streamingChatModel.chat(question, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String token) {
                    firstToken.compareAndSet(0, System.nanoTime());
                    write(out, token);          // send this token to the client right now
                }

                @Override
                public void onCompleteResponse(ChatResponse response) {
                    done.complete(response);
                }

                @Override
                public void onError(Throwable error) {
                    done.completeExceptionally(error);
                }
            });

            done.join();
            long ttft = (firstToken.get() - start) / 1_000_000;
            long total = (System.nanoTime() - start) / 1_000_000;
            write(out, "\n\n[first token after " + ttft + " ms, total " + total + " ms]\n");
        };
        // nosniff stops browsers from buffering the start of the response
        return ResponseEntity.ok().header("X-Content-Type-Options", "nosniff").body(body);
    }

    private static void write(OutputStream out, String text) {
        try {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
