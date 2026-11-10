package com.conceptgraph.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Finds a cycle in a directed graph, or proves there is none (POC 6.1).
 *
 * Why it matters here: a learning order exists only if REQUIRES has no cycle. If A requires B and B requires A,
 * nobody can learn either first. The graph database would happily store that cycle, so the check runs before
 * anything is written.
 *
 * Method: depth-first search with three colours. WHITE = not visited, GRAY = on the current path, BLACK = done.
 * Reaching a GRAY node means we walked back into our own path, and the path from that node onwards is the cycle.
 */
final class CycleDetector {

    private enum Colour { WHITE, GRAY, BLACK }

    private CycleDetector() {
    }

    /**
     * @param edges node id -> the ids it points to (for REQUIRES: concept -> its prerequisites)
     * @return the cycle as a closed path, for example [rag, graph-rag, rag]; empty if the graph is acyclic
     */
    static Optional<List<String>> findCycle(Map<String, List<String>> edges) {
        // Sorted, so the same input always reports the same cycle (easier to read, easier to test)
        Map<String, Colour> colour = new HashMap<>();
        for (String node : new TreeSet<>(edges.keySet())) {
            colour.put(node, Colour.WHITE);
        }
        List<String> path = new ArrayList<>();
        for (String node : new TreeSet<>(edges.keySet())) {
            if (colour.get(node) == Colour.WHITE) {
                Optional<List<String>> cycle = visit(node, edges, colour, path);
                if (cycle.isPresent()) {
                    return cycle;
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<List<String>> visit(String node, Map<String, List<String>> edges,
                                                Map<String, Colour> colour, List<String> path) {
        colour.put(node, Colour.GRAY);
        path.add(node);
        for (String next : new TreeSet<>(edges.getOrDefault(node, List.of()))) {
            // Targets outside the map are caught by ConceptGraphModel's "unknown concept" check, not here
            Colour state = colour.getOrDefault(next, Colour.BLACK);
            if (state == Colour.GRAY) {
                // We came back to a node on the current path: from there to here is the cycle
                List<String> cycle = new ArrayList<>(path.subList(path.indexOf(next), path.size()));
                cycle.add(next);
                return Optional.of(cycle);
            }
            if (state == Colour.WHITE) {
                Optional<List<String>> cycle = visit(next, edges, colour, path);
                if (cycle.isPresent()) {
                    return cycle;
                }
            }
        }
        path.remove(path.size() - 1);
        colour.put(node, Colour.BLACK);
        return Optional.empty();
    }
}
