package com.conceptgraph.lessons.poc03;

import com.conceptgraph.rag.RagService;
import com.conceptgraph.retrieval.VectorMath;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * POC 3, lesson 2: retrieval on its own, before any LLM is involved. GET
 * /poc03/search the k chunks closest to the question, with scores
 */
@RestController
@RequestMapping("/poc03")
public class Poc03SearchController {

	private final RagService rag;

	public Poc03SearchController(RagService rag) {
		this.rag = rag;
	}

	@Tag(name = "POC 3.2 Retrieval", description = "Top-k search in Qdrant, no LLM")
	@Operation(summary = "Which chunks would the answer be built from?", description = "Try a question that is in the notes, one that is half in the notes, and one that is not "
			+ "(for example 'What is the capital of France?'). Compare the scores: is there a clear gap "
			+ "between relevant and irrelevant chunks?")
	@GetMapping("/search")
	public List<Map<String, Object>> search(@RequestParam(defaultValue = "What is chunking?") String question,
			@RequestParam(defaultValue = "3") int k) {
		return rag.retrieve(question, k).stream().map(Poc03SearchController::row).toList();
	}

	private static Map<String, Object> row(EmbeddingMatch<TextSegment> m) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("conceptId", m.embedded().metadata().getString("conceptId"));
		row.put("section", m.embedded().metadata().getString("section"));
		row.put("score", VectorMath.round(m.score(), 3));
		row.put("text", m.embedded().text());
		return row;
	}
}
