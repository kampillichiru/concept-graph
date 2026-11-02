package com.conceptgraph.lessons.poc04;

import com.conceptgraph.rag.RagService;
import com.conceptgraph.rag.RagService.RagAnswer;
import com.conceptgraph.tracing.Traces;
import io.opentelemetry.api.trace.Tracer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * POC 4, lessons 1 and 2: one question, one trace.
 * GET /poc04/ask  answers a question (the POC 3 RAG flow) and returns the traceId of the work it caused.
 * Then look at the trace with GET /poc04/traces/{traceId}.
 */
@RestController
@RequestMapping("/poc04")
public class Poc04AskController {

    private final RagService rag;
    private final Tracer tracer;

    public Poc04AskController(RagService rag, Tracer tracer) {
        this.rag = rag;
        this.tracer = tracer;
    }

    @Tag(name = "POC 4.1 One question, one trace", description = "Spans for retrieval, embedding and the LLM call")
    @Operation(summary = "Answer a question and return its trace id",
            description = "The root span 'poc04.ask' contains 'rag.answer', which contains 'rag.retrieve' "
                    + "(with 'embed' and 'qdrant.search') and 'chat'. Copy the traceId and open "
                    + "/poc04/traces/{traceId} to see the tree with timings. Try a question that is not in the notes: "
                    + "compare the chat span's token counts with the question above.")
    @GetMapping("/ask")
    public Map<String, Object> ask(@RequestParam(defaultValue = "Why do we split notes into chunks?") String question,
                                   @RequestParam(defaultValue = "3") int k) {
        return Traces.inSpan(tracer, "poc04.ask", span -> {
            span.setAttribute("question", question);
            RagAnswer answer = rag.answer(question, k);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("traceId", span.getSpanContext().getTraceId());
            response.put("answer", answer.answer());
            response.put("citedIds", answer.citedIds());
            response.put("unknownCitations", answer.unknownCitations());
            response.put("sourceCount", answer.sources().size());
            return response;
        });
    }
}
