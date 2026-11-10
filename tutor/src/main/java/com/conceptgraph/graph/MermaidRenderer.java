package com.conceptgraph.graph;

import com.conceptgraph.graph.GraphQueries.Edge;
import com.conceptgraph.graph.GraphQueries.PathStep;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws a learning path as a Mermaid flowchart (POC 6.4). Paste the output into https://mermaid.live to see it.
 *
 * Arrows point from prerequisite to dependent, so the diagram reads left to right in learning order.
 * Node ids are n0, n1, ... because concept ids contain hyphens, which Mermaid does not accept. The readable
 * name goes into the label.
 */
public final class MermaidRenderer {

    private MermaidRenderer() {
    }

    public static String learningPath(String targetId, String targetTitle, List<PathStep> steps, List<Edge> requires) {
        Map<String, String> nodeIds = new LinkedHashMap<>();
        for (PathStep step : steps) {
            nodeIds.putIfAbsent(step.id(), "n" + nodeIds.size());
        }
        nodeIds.putIfAbsent(targetId, "n" + nodeIds.size());

        Map<String, String> titles = new LinkedHashMap<>();
        steps.forEach(s -> titles.put(s.id(), s.title()));
        titles.put(targetId, targetTitle);

        List<String> lines = new ArrayList<>();
        lines.add("flowchart LR");
        nodeIds.forEach((id, node) -> lines.add("    " + node + "[\"" + escape(titles.get(id)) + "\"]"
                + (id.equals(targetId) ? ":::target" : "")));
        for (Edge edge : requires) {
            // edge.requirer requires edge.prerequisite: draw prerequisite --> requirer
            lines.add("    " + nodeIds.get(edge.prerequisite()) + " --> " + nodeIds.get(edge.requirer()));
        }
        lines.add("    classDef target fill:#fde68a,stroke:#b45309,stroke-width:2px");
        return String.join("\n", lines) + "\n";
    }

    /** Mermaid labels cannot contain a raw double quote; #quot; is its escape. */
    private static String escape(String text) {
        return text.replace("\"", "#quot;");
    }
}
