package com.conceptgraph.lessons.poc06;

import com.conceptgraph.graph.GraphQueries;
import com.conceptgraph.graph.GraphQueries.Edge;
import com.conceptgraph.graph.MermaidRenderer;
import com.conceptgraph.graph.GraphQueries.PathStep;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * POC 6, lesson 6.4: draw the learning path as a diagram.
 */
@RestController
@RequestMapping("/poc06")
public class Poc06MermaidController {

    private final GraphQueries queries;

    public Poc06MermaidController(GraphQueries queries) {
        this.queries = queries;
    }

    @Tag(name = "POC 6.4 Learning path diagram", description = "The path as Mermaid text, for mermaid.live")
    @Operation(summary = "Learning path as a Mermaid flowchart",
            description = "Returns plain text. Paste it into https://mermaid.live. Arrows point from prerequisite to "
                    + "dependent, so the diagram reads left to right in learning order. The target is highlighted. "
                    + "Built from two Cypher results: the path, and the REQUIRES edges between those concepts.")
    @GetMapping(value = "/path/mermaid", produces = MediaType.TEXT_PLAIN_VALUE)
    public String mermaid(@RequestParam(defaultValue = "graph-rag") String conceptId) {
        String title = queries.titleOf(conceptId);
        List<PathStep> steps = queries.learningPath(conceptId).rows();
        List<String> ids = new ArrayList<>();
        steps.forEach(s -> ids.add(s.id()));
        ids.add(conceptId);
        List<Edge> edges = queries.requiresAmong(ids).rows();
        return MermaidRenderer.learningPath(conceptId, title, steps, edges);
    }
}
