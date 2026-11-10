package com.conceptgraph.graph;

import com.conceptgraph.knowledge.ConceptNote;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The graph as plain Java, before it goes to Neo4j (POC 6.1).
 *
 * Built from the notes' front matter, and checked here: every reference must point at a real concept, ids must be
 * unique, and REQUIRES must have no cycle. Only a model that passes all of that is written to the database.
 * Checking in Java first means a bad note gives a clear error, instead of a half-written graph.
 *
 * Edge rules (the data-modelling choices the lesson explains):
 * - REQUIRES and PART_OF are directed: "graph-rag requires rag".
 * - RELATED_TO and ALTERNATIVE_TO are symmetric. They are stored ONCE, from the smaller id to the larger, so that
 *   "a lists b" and "b lists a" become one relationship, not two.
 */
public record ConceptGraphModel(List<ConceptNote> concepts, Set<String> categories, List<Edge> edges) {

    public enum EdgeType {
        REQUIRES(false), RELATED_TO(true), ALTERNATIVE_TO(true), PART_OF(false);

        /** True if the relationship has no direction, so it is stored once with the smaller id first. */
        public final boolean symmetric;

        EdgeType(boolean symmetric) {
            this.symmetric = symmetric;
        }
    }

    public record Edge(EdgeType type, String from, String to) {
    }

    /** Builds and validates the model. Throws IllegalArgumentException with every problem it found. */
    public static ConceptGraphModel from(List<ConceptNote> notes) {
        Map<String, ConceptNote> byId = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        for (ConceptNote note : notes) {
            if (byId.put(note.id(), note) != null) {
                problems.add("duplicate concept id '" + note.id() + "'");
            }
        }

        Set<Edge> edges = new LinkedHashSet<>();
        for (ConceptNote note : notes) {
            addEdges(EdgeType.REQUIRES, note, note.requires(), byId, edges, problems);
            addEdges(EdgeType.RELATED_TO, note, note.related(), byId, edges, problems);
            addEdges(EdgeType.ALTERNATIVE_TO, note, note.alternativeTo(), byId, edges, problems);
            addEdges(EdgeType.PART_OF, note, note.partOf(), byId, edges, problems);
        }
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", problems));
        }

        Map<String, List<String>> requires = byId.keySet().stream().collect(Collectors.toMap(
                Function.identity(),
                id -> edges.stream()
                        .filter(e -> e.type() == EdgeType.REQUIRES && e.from().equals(id))
                        .map(Edge::to).toList()));
        CycleDetector.findCycle(requires).ifPresent(cycle -> {
            throw new IllegalArgumentException("cycle in REQUIRES: " + String.join(" -> ", cycle)
                    + ". A concept cannot come before itself, so no learning order exists. Break the loop in one note.");
        });

        Set<String> categories = new TreeSet<>();
        notes.stream().map(ConceptNote::category).filter(c -> c != null && !c.isBlank()).forEach(categories::add);

        List<Edge> sorted = edges.stream()
                .sorted(Comparator.comparing((Edge e) -> e.type().name())
                        .thenComparing(Edge::from).thenComparing(Edge::to))
                .toList();
        return new ConceptGraphModel(List.copyOf(notes), categories, sorted);
    }

    private static void addEdges(EdgeType type, ConceptNote note, List<String> targets,
                                 Map<String, ConceptNote> byId, Set<Edge> edges, List<String> problems) {
        for (String target : targets) {
            if (!byId.containsKey(target)) {
                problems.add(note.id() + " " + type.name().toLowerCase().replace('_', ' ')
                        + " unknown concept '" + target + "'");
                continue;
            }
            if (target.equals(note.id())) {
                problems.add(note.id() + " " + type.name().toLowerCase().replace('_', ' ') + " itself");
                continue;
            }
            if (type.symmetric && target.compareTo(note.id()) < 0) {
                edges.add(new Edge(type, target, note.id()));   // smaller id first, so the pair is stored once
            } else {
                edges.add(new Edge(type, note.id(), target));
            }
        }
    }
}
