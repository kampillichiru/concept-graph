package com.conceptgraph.lessons.poc06;

import com.conceptgraph.graph.GraphQueries;
import com.conceptgraph.graph.GraphQueries.Concept;
import com.conceptgraph.graph.GraphQueries.Hub;
import com.conceptgraph.graph.GraphQueries.Neighbour;
import com.conceptgraph.graph.GraphQueries.QueryResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * POC 6, lesson 6.3: explore the shape of the graph: neighbours, roots, hubs.
 */
@RestController
@RequestMapping("/poc06")
public class Poc06ExploreController {

    public record Neighbours(String conceptId, String title, List<Neighbour> neighbours, String cypher, long millis) {
    }

    public record Roots(List<Concept> concepts, String cypher, long millis) {
    }

    public record Hubs(List<Hub> hubs, String cypher, long millis) {
    }

    private final GraphQueries queries;

    public Poc06ExploreController(GraphQueries queries) {
        this.queries = queries;
    }

    @Tag(name = "POC 6.3 Explore the graph", description = "Neighbours, roots (no prerequisites), most connected")
    @Operation(summary = "Everything directly connected to a concept, in both directions",
            description = "Default rag. 'direction' is 'out' when this concept is the start of the relationship "
                    + "(rag REQUIRES embeddings is out), 'in' when another concept points at it. RELATED_TO and "
                    + "ALTERNATIVE_TO are symmetric, so they show 'both': they are stored once, with the smaller id "
                    + "first, and that storage order means nothing.")
    @GetMapping("/neighbours")
    public Neighbours neighbours(@RequestParam(defaultValue = "rag") String conceptId) {
        String title = queries.titleOf(conceptId);
        QueryResult<Neighbour> result = queries.neighbours(conceptId);
        return new Neighbours(conceptId, title, result.rows(), result.cypher(), result.millis());
    }

    @Tag(name = "POC 6.3 Explore the graph", description = "Neighbours, roots (no prerequisites), most connected")
    @Operation(summary = "Concepts that require nothing (the foundations)",
            description = "Observe: these are the first things anyone must learn. The check is a pattern predicate: "
                    + "WHERE NOT (c)-[:REQUIRES]->().")
    @GetMapping("/no-prerequisites")
    public Roots noPrerequisites() {
        QueryResult<Concept> result = queries.noPrerequisites();
        return new Roots(result.rows(), result.cypher(), result.millis());
    }

    @Tag(name = "POC 6.3 Explore the graph", description = "Neighbours, roots (no prerequisites), most connected")
    @Operation(summary = "The most connected concepts (degree, both directions)",
            description = "Observe: the hubs are the concepts that everything else is measured against. "
                    + "Compare the top of this list with the learning path you get for them.")
    @GetMapping("/most-connected")
    public Hubs mostConnected(@RequestParam(defaultValue = "5") int limit) {
        QueryResult<Hub> result = queries.mostConnected(limit);
        return new Hubs(result.rows(), result.cypher(), result.millis());
    }
}
