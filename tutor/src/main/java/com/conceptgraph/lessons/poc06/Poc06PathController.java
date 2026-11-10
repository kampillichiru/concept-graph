package com.conceptgraph.lessons.poc06;

import com.conceptgraph.graph.GraphQueries;
import com.conceptgraph.graph.GraphQueries.Concept;
import com.conceptgraph.graph.GraphQueries.PathStep;
import com.conceptgraph.graph.GraphQueries.QueryResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * POC 6, lesson 6.2: "What do I need before this?" answered by Cypher alone, with no LLM.
 */
@RestController
@RequestMapping("/poc06")
public class Poc06PathController {

    public record Step(int order, String id, String title, int depth) {
    }

    public record LearningPath(String conceptId, String title, List<Step> learningOrder, String cypher, long millis) {
    }

    public record Prerequisites(String conceptId, String title, List<Concept> prerequisites, String cypher,
                                long millis) {
    }

    private final GraphQueries queries;

    public Poc06PathController(GraphQueries queries) {
        this.queries = queries;
    }

    @Tag(name = "POC 6.2 Learning path", description = "Variable-length REQUIRES paths, ordered foundations first")
    @Operation(summary = "The full learning path for a concept",
            description = "Default graph-rag. Read learningOrder top to bottom: it starts with foundations (highest depth) "
                    + "and ends just before the target. Depth is the longest REQUIRES chain from the target, so a concept "
                    + "needed by two others is placed before both. Try conceptId=langgraph ('What before LangGraph?'). "
                    + "The 'cypher' field is the exact query that produced the answer.")
    @GetMapping("/path")
    public LearningPath path(@RequestParam(defaultValue = "graph-rag") String conceptId) {
        String title = queries.titleOf(conceptId);
        QueryResult<PathStep> result = queries.learningPath(conceptId);
        List<Step> order = new ArrayList<>();
        for (int i = 0; i < result.rows().size(); i++) {
            PathStep row = result.rows().get(i);
            order.add(new Step(i + 1, row.id(), row.title(), row.depth()));
        }
        return new LearningPath(conceptId, title, order, result.cypher(), result.millis());
    }

    @Tag(name = "POC 6.2 Learning path", description = "Variable-length REQUIRES paths, ordered foundations first")
    @Operation(summary = "Direct prerequisites only (one hop)",
            description = "Compare with /poc06/path for the same concept. The difference is the transitive closure: "
                    + "the prerequisites of the prerequisites. This is what a single JOIN or one relational query "
                    + "would give you. The path query is the one that needs recursion in SQL.")
    @GetMapping("/prerequisites")
    public Prerequisites prerequisites(@RequestParam(defaultValue = "graph-rag") String conceptId) {
        String title = queries.titleOf(conceptId);
        QueryResult<Concept> result = queries.directPrerequisites(conceptId);
        return new Prerequisites(conceptId, title, result.rows(), result.cypher(), result.millis());
    }
}
