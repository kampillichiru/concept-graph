package com.conceptgraph.graph;

import com.conceptgraph.graph.ConceptGraphModel.Edge;
import com.conceptgraph.graph.ConceptGraphModel.EdgeType;
import com.conceptgraph.knowledge.ConceptNote;
import com.conceptgraph.knowledge.NoteLoader;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads the notes' front matter into Neo4j (POC 6.1).
 *
 * One write transaction does all of it:
 * 1. delete the old Concept and Category nodes (DETACH DELETE also removes their relationships)
 * 2. MERGE the concepts, categories and edges again
 *
 * Why delete-then-MERGE and not MERGE alone? MERGE adds and updates, but it never removes. If a note drops a
 * "requires" line, MERGE alone would keep the old edge forever. Rebuilding inside one transaction avoids stale edges,
 * and if anything fails the transaction rolls back, so the old graph is still there.
 */
@Component
public class GraphLoader {

    /** What was (or would be) written. dryRun=true returns the same counts without touching Neo4j. */
    public record LoadReport(boolean dryRun, int concepts, int categories, int requires, int relatedTo,
                             int alternativeTo, int partOf, long millis) {
    }

    private final NoteLoader notes;
    private final Driver driver;

    public GraphLoader(NoteLoader notes, Driver driver) {
        this.notes = notes;
        this.driver = driver;
    }

    /** Validates first. Invalid notes throw IllegalArgumentException, and nothing is written. */
    public LoadReport load(boolean dryRun) {
        long start = System.nanoTime();
        List<ConceptNote> loaded = notes.loadAll();
        ConceptGraphModel model = ConceptGraphModel.from(loaded);
        if (!dryRun) {
            write(model);
        }
        return new LoadReport(dryRun,
                model.concepts().size(),
                model.categories().size(),
                countOf(model, EdgeType.REQUIRES),
                countOf(model, EdgeType.RELATED_TO),
                countOf(model, EdgeType.ALTERNATIVE_TO),
                countOf(model, EdgeType.PART_OF),
                (System.nanoTime() - start) / 1_000_000);
    }

    private void write(ConceptGraphModel model) {
        try (Session session = driver.session()) {
            session.executeWrite(tx -> {
                tx.run("MATCH (n) WHERE n:Concept OR n:Category DETACH DELETE n");

                tx.run("UNWIND $names AS name MERGE (:Category {name: name})",
                        Map.of("names", List.copyOf(model.categories())));

                List<Map<String, Object>> concepts = new ArrayList<>();
                List<Map<String, Object>> inCategory = new ArrayList<>();
                for (ConceptNote note : model.concepts()) {
                    concepts.add(Map.of("id", note.id(), "title", note.title(), "level", note.level()));
                    if (note.category() != null && !note.category().isBlank()) {
                        inCategory.add(Map.of("id", note.id(), "category", note.category()));
                    }
                }
                tx.run("UNWIND $rows AS r MERGE (c:Concept {id: r.id}) SET c.title = r.title, c.level = r.level",
                        Map.of("rows", concepts));
                tx.run("UNWIND $rows AS r MATCH (c:Concept {id: r.id}), (cat:Category {name: r.category}) "
                        + "MERGE (c)-[:IN_CATEGORY]->(cat)", Map.of("rows", inCategory));

                // One query per edge type. The type comes from our enum (never from user input), so putting it in
                // the query text is safe; Cypher cannot take a relationship type as a parameter.
                for (EdgeType type : EdgeType.values()) {
                    List<Map<String, Object>> rows = new ArrayList<>();
                    for (Edge edge : model.edges()) {
                        if (edge.type() == type) {
                            rows.add(Map.of("from", edge.from(), "to", edge.to()));
                        }
                    }
                    if (!rows.isEmpty()) {
                        tx.run("UNWIND $rows AS r MATCH (a:Concept {id: r.from}), (b:Concept {id: r.to}) "
                                + "MERGE (a)-[:" + type.name() + "]->(b)", Map.of("rows", rows));
                    }
                }
                return null;
            });
        }
    }

    private static int countOf(ConceptGraphModel model, EdgeType type) {
        return (int) model.edges().stream().filter(e -> e.type() == type).count();
    }
}
