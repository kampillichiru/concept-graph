package com.conceptgraph.graph;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The read-only questions the graph answers, all as Cypher (POC 6.2 to 6.4). No LLM is involved: the graph
 * decides the structure, the language model would only explain it (architecture rule 2).
 *
 * The Cypher strings are constants on purpose. The controllers return them with each answer, so you can read the
 * query that produced the result.
 */
@Component
public class GraphQueries {

    /** Everything the concept depends on, directly or through other concepts, ordered foundations-first. */
    static final String LEARNING_PATH = """
            MATCH path = (t:Concept {id: $id})-[:REQUIRES*1..]->(p:Concept)
            WITH p, max(length(path)) AS depth
            RETURN p.id AS id, p.title AS title, depth
            ORDER BY depth DESC, id""";

    /** One hop only: the concept's own "requires" line. */
    static final String DIRECT_PREREQUISITES = """
            MATCH (t:Concept {id: $id})-[:REQUIRES]->(p:Concept)
            RETURN p.id AS id, p.title AS title
            ORDER BY id""";

    /**
     * Every relationship in either direction. direction: "out" = this concept is the start (it requires / is part of
     * the other), "in" = the other concept points at it, "both" = symmetric type, where the stored direction is
     * only the storage order (smaller id first) and means nothing.
     */
    static final String NEIGHBOURS = """
            MATCH (c:Concept {id: $id})-[r]-(n:Concept)
            RETURN type(r) AS relation,
                   CASE WHEN type(r) IN ['RELATED_TO', 'ALTERNATIVE_TO'] THEN 'both'
                        WHEN startNode(r) = c THEN 'out' ELSE 'in' END AS direction,
                   n.id AS id, n.title AS title
            ORDER BY relation, direction, id""";

    /** Concepts nobody needs first: they require nothing. */
    static final String NO_PREREQUISITES = """
            MATCH (c:Concept)
            WHERE NOT (c)-[:REQUIRES]->()
            RETURN c.id AS id, c.title AS title
            ORDER BY id""";

    /** Most relationships in total, counted in both directions. */
    static final String MOST_CONNECTED = """
            MATCH (c:Concept)-[r]-(n:Concept)
            RETURN c.id AS id, c.title AS title, count(r) AS degree
            ORDER BY degree DESC, id
            LIMIT $limit""";

    /** The REQUIRES edges between a set of concepts: what the Mermaid diagram draws. */
    static final String REQUIRES_AMONG = """
            MATCH (a:Concept)-[:REQUIRES]->(b:Concept)
            WHERE a.id IN $ids AND b.id IN $ids
            RETURN a.id AS requirer, b.id AS prerequisite
            ORDER BY requirer, prerequisite""";

    static final String CONCEPT_TITLE = "MATCH (c:Concept {id: $id}) RETURN c.title AS title";

    static final String NODE_COUNTS = """
            MATCH (n) RETURN labels(n)[0] AS label, count(*) AS count ORDER BY label""";

    static final String RELATIONSHIP_COUNTS = """
            MATCH ()-[r]->() RETURN type(r) AS type, count(*) AS count ORDER BY type""";

    /** A query result plus the Cypher that produced it and how long it took. */
    public record QueryResult<T>(String cypher, long millis, List<T> rows) {
    }

    public record Concept(String id, String title) {
    }

    /** depth = the longest REQUIRES chain from the target down to this concept. Bigger depth = learn it earlier. */
    public record PathStep(String id, String title, int depth) {
    }

    public record Neighbour(String relation, String direction, String id, String title) {
    }

    public record Hub(String id, String title, long degree) {
    }

    public record Edge(String requirer, String prerequisite) {
    }

    public record Stats(Map<String, Long> nodesByLabel, Map<String, Long> relationshipsByType) {
    }

    private final Driver driver;

    public GraphQueries(Driver driver) {
        this.driver = driver;
    }

    /** The concept's title, or ConceptNotFoundException. Checked first so a wrong id is a 404, not an empty answer. */
    public String titleOf(String conceptId) {
        QueryResult<String> result = run(CONCEPT_TITLE, Map.of("id", conceptId), r -> r.get("title").asString());
        if (result.rows().isEmpty()) {
            throw new ConceptNotFoundException(conceptId);
        }
        return result.rows().get(0);
    }

    public QueryResult<PathStep> learningPath(String conceptId) {
        return run(LEARNING_PATH, Map.of("id", conceptId),
                r -> new PathStep(r.get("id").asString(), r.get("title").asString(), r.get("depth").asInt()));
    }

    public QueryResult<Concept> directPrerequisites(String conceptId) {
        return run(DIRECT_PREREQUISITES, Map.of("id", conceptId),
                r -> new Concept(r.get("id").asString(), r.get("title").asString()));
    }

    public QueryResult<Neighbour> neighbours(String conceptId) {
        return run(NEIGHBOURS, Map.of("id", conceptId),
                r -> new Neighbour(r.get("relation").asString(), r.get("direction").asString(),
                        r.get("id").asString(), r.get("title").asString()));
    }

    public QueryResult<Concept> noPrerequisites() {
        return run(NO_PREREQUISITES, Map.of(),
                r -> new Concept(r.get("id").asString(), r.get("title").asString()));
    }

    public QueryResult<Hub> mostConnected(int limit) {
        return run(MOST_CONNECTED, Map.of("limit", limit),
                r -> new Hub(r.get("id").asString(), r.get("title").asString(), r.get("degree").asLong()));
    }

    public QueryResult<Edge> requiresAmong(List<String> conceptIds) {
        return run(REQUIRES_AMONG, Map.of("ids", conceptIds),
                r -> new Edge(r.get("requirer").asString(), r.get("prerequisite").asString()));
    }

    public Stats stats() {
        Map<String, Long> labels = toMap(run(NODE_COUNTS, Map.of(),
                r -> Map.entry(r.get("label").asString(), r.get("count").asLong())));
        Map<String, Long> types = toMap(run(RELATIONSHIP_COUNTS, Map.of(),
                r -> Map.entry(r.get("type").asString(), r.get("count").asLong())));
        return new Stats(labels, types);
    }

    private static Map<String, Long> toMap(QueryResult<Map.Entry<String, Long>> result) {
        Map<String, Long> map = new LinkedHashMap<>();
        result.rows().forEach(e -> map.put(e.getKey(), e.getValue()));
        return map;
    }

    /**
     * Runs one read transaction. executeRead lets the driver retry on a transient failure, which is safe because
     * a read has no side effects.
     */
    private <T> QueryResult<T> run(String cypher, Map<String, Object> params,
                                   Function<org.neo4j.driver.Record, T> mapper) {
        long start = System.nanoTime();
        try (Session session = driver.session()) {
            List<T> rows = session.executeRead(tx -> tx.run(cypher, params).list(mapper));
            return new QueryResult<>(cypher, (System.nanoTime() - start) / 1_000_000, rows);
        }
    }
}
