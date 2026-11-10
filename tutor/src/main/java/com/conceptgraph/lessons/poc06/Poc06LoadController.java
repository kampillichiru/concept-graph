package com.conceptgraph.lessons.poc06;

import com.conceptgraph.graph.GraphLoader;
import com.conceptgraph.graph.GraphLoader.LoadReport;
import com.conceptgraph.graph.GraphQueries;
import com.conceptgraph.graph.GraphQueries.Stats;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * POC 6, lesson 6.1: turn the notes' front matter into nodes and relationships.
 */
@RestController
@RequestMapping("/poc06")
public class Poc06LoadController {

    private final GraphLoader loader;
    private final GraphQueries queries;

    public Poc06LoadController(GraphLoader loader, GraphQueries queries) {
        this.loader = loader;
        this.queries = queries;
    }

    @Tag(name = "POC 6.1 Load the graph", description = "Front matter -> Concept nodes and relationships in Neo4j")
    @Operation(summary = "Validate the notes and load them into Neo4j",
            description = "Checks every reference and REQUIRES for cycles in plain Java first. If any problem is found, "
                    + "nothing is written and the answer is 422 with the problem. dryRun=true runs only the checks. "
                    + "Run this twice: the counts are the same, because the load replaces the graph in one transaction "
                    + "(MERGE and DETACH DELETE). Then try the break-it exercise in docs/lessons/poc06.md.")
    @PostMapping("/load")
    public LoadReport load(@RequestParam(defaultValue = "false") boolean dryRun) {
        return loader.load(dryRun);
    }

    @Tag(name = "POC 6.1 Load the graph", description = "Front matter -> Concept nodes and relationships in Neo4j")
    @Operation(summary = "Count nodes by label and relationships by type",
            description = "Observe: Concept should equal the number of notes in knowledge-base/concepts, and each "
                    + "relationship type has one count. Run the load again and compare: the numbers must not change.")
    @GetMapping("/stats")
    public Stats stats() {
        return queries.stats();
    }
}
