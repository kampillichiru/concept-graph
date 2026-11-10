package com.conceptgraph.graph;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CycleDetectorTest {

    @Test
    void acyclicGraphHasNoCycle() {
        // a requires b, b requires c: a chain, no loop
        Map<String, List<String>> edges = Map.of("a", List.of("b"), "b", List.of("c"), "c", List.of());
        assertTrue(CycleDetector.findCycle(edges).isEmpty());
    }

    @Test
    void twoNodeLoopIsReportedAsAClosedPath() {
        Map<String, List<String>> edges = Map.of("rag", List.of("graph-rag"), "graph-rag", List.of("rag"));
        assertEquals(List.of("graph-rag", "rag", "graph-rag"),
                CycleDetector.findCycle(edges).orElseThrow());
    }

    @Test
    void selfLoopIsACycle() {
        assertEquals(List.of("a", "a"), CycleDetector.findCycle(Map.of("a", List.of("a"))).orElseThrow());
    }

    @Test
    void longerLoopIsFoundWithoutTheDetourBeforeIt() {
        // x -> a -> b -> c -> b: the cycle is b, c, b. The entry point x is not part of it.
        Map<String, List<String>> edges = Map.of(
                "x", List.of("a"), "a", List.of("b"), "b", List.of("c"), "c", List.of("b"));
        assertEquals(List.of("b", "c", "b"), CycleDetector.findCycle(edges).orElseThrow());
    }

    @Test
    void diamondIsNotACycle() {
        // a -> b, a -> c, b -> d, c -> d: two routes to d, but no loop. Common in real prerequisite graphs.
        Map<String, List<String>> edges = Map.of(
                "a", List.of("b", "c"), "b", List.of("d"), "c", List.of("d"), "d", List.of());
        assertTrue(CycleDetector.findCycle(edges).isEmpty());
    }
}
