package com.conceptgraph.lessons.poc01;

import com.conceptgraph.tutor.StatelessTutorChat;
import com.conceptgraph.tutor.TutorChat;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * POC 1, lesson 4: chat memory.
 * POST   /poc01/chat/{conversationId}          body = your message (plain text)
 * POST   /poc01/chat/{conversationId}?memory=false   same, without memory
 * GET    /poc01/chat/{conversationId}/memory   what is stored (and re-sent on every call)
 * DELETE /poc01/chat/{conversationId}/memory   forget the conversation
 */
@Tag(name = "POC 1.4 Chat memory", description = "Memory = re-sending history, stored per conversation id")
@RestController
@RequestMapping("/poc01/chat")
public class Poc01MemoryController {

    private final TutorChat tutorChat;
    private final StatelessTutorChat statelessTutorChat;
    private final ChatMemoryStore store;

    public Poc01MemoryController(TutorChat tutorChat, StatelessTutorChat statelessTutorChat, ChatMemoryStore store) {
        this.tutorChat = tutorChat;
        this.statelessTutorChat = statelessTutorChat;
        this.store = store;
    }

    @Operation(summary = "Send a chat message",
            description = "Send 'What are embeddings?' then 'Give me an example of it.' with memory=false, "
                    + "then with memory=true. Then try another conversationId (e.g. bob).")
    @PostMapping(value = "/{conversationId}", consumes = MediaType.TEXT_PLAIN_VALUE)
    public Map<String, String> chat(@PathVariable String conversationId,
                                    @RequestParam(defaultValue = "true") boolean memory,
                                    @RequestBody String message) {
        String reply = memory
                ? tutorChat.chat(conversationId, message)
                : statelessTutorChat.chat(message);
        return Map.of("you", message, "tutor", reply, "memory", String.valueOf(memory));
    }

    /** Shows the history LangChain4j will send to the model on the next call. */
    @Operation(summary = "Show what is stored for a conversation",
            description = "All of this is re-sent to the model on the next call (and costs tokens).")
    @GetMapping("/{conversationId}/memory")
    public List<Map<String, String>> memory(@PathVariable String conversationId) {
        List<ChatMessage> messages = store.getMessages(conversationId);
        return messages.stream()
                .map(m -> Map.of("type", m.type().toString(), "text", textOf(m)))
                .toList();
    }

    private static String textOf(ChatMessage m) {
        return switch (m) {
            case SystemMessage s -> s.text();
            case UserMessage u -> u.singleText();
            case AiMessage a -> a.text();
            default -> m.toString();
        };
    }

    @Operation(summary = "Forget a conversation")
    @DeleteMapping("/{conversationId}/memory")
    public void forget(@PathVariable String conversationId) {
        store.deleteMessages(conversationId);
    }
}
