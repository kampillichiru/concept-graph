package com.conceptgraph.lessons.poc03;

import com.conceptgraph.knowledge.ChunkStrategy;
import com.conceptgraph.knowledge.Chunk;
import com.conceptgraph.knowledge.ConceptIngestor;
import com.conceptgraph.knowledge.ConceptIngestor.IngestReport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * POC 3, lesson 1: chunking and ingestion.
 * GET  /poc03/chunks  preview how the notes are split (no LLM, no Qdrant)
 * POST /poc03/ingest  embed every chunk and upsert it into Qdrant (safe to run again)
 */
@RestController
@RequestMapping("/poc03")
public class Poc03IngestController {

    private final ConceptIngestor ingestor;

    public Poc03IngestController(ConceptIngestor ingestor) {
        this.ingestor = ingestor;
    }

    @Tag(name = "POC 3.1 Chunking", description = "Notes -> sections -> chunks")
    @Operation(summary = "Preview the chunks",
            description = "One chunk per '## ' section. Check that each chunk makes sense on its own. "
                    + "Is any chunk too long, too short, or split in the wrong place?")
    @GetMapping("/chunks")
    public List<Map<String, Object>> chunks() {
        return ingestor.chunks().stream().map(Poc03IngestController::row).toList();
    }

    @Tag(name = "POC 3.1 Ingestion", description = "Notes -> embeddings -> Qdrant")
    @Operation(summary = "Embed all chunks and upsert them into Qdrant",
            description = "SECTION writes collection ai_concepts (what the tutor answers from). FIXED writes "
                    + "ai_concepts_fixed (the baseline for POC 3.6). Run it twice: the point count must not grow "
                    + "(idempotent upsert).")
    @PostMapping("/ingest")
    public IngestReport ingest(@RequestParam(defaultValue = "SECTION") ChunkStrategy strategy) {
        return ingestor.ingest(strategy);
    }

    private static Map<String, Object> row(Chunk c) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("chunkId", c.id());
        row.put("conceptId", c.conceptId());
        row.put("section", c.section());
        row.put("chars", c.text().length());
        row.put("text", c.text());
        return row;
    }
}
