package com.conceptgraph.graph;

import com.conceptgraph.config.ConceptGraphProperties;
import com.conceptgraph.graph.ConceptGraphModel.Edge;
import com.conceptgraph.graph.ConceptGraphModel.EdgeType;
import com.conceptgraph.knowledge.ConceptNote;
import com.conceptgraph.knowledge.NoteLoader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConceptGraphModelTest {

    /** Only the fields the graph uses; the body is irrelevant here. */
    private static ConceptNote note(String id, List<String> requires, List<String> related,
                                    List<String> alternativeTo) {
        return new ConceptNote(id, id, "Test", 1, requires, related, alternativeTo, List.of(), "");
    }

    @Test
    void buildsEdgesOfEveryTypeAndCountsCategories() {
        ConceptGraphModel model = ConceptGraphModel.from(List.of(
                note("rag", List.of("llm"), List.of(), List.of()),
                note("llm", List.of(), List.of(), List.of())));
        assertEquals(2, model.concepts().size());
        assertEquals(List.of(new Edge(EdgeType.REQUIRES, "rag", "llm")), model.edges());
        assertEquals(List.of("Test"), List.copyOf(model.categories()));
    }

    @Test
    void symmetricEdgeListedByBothSidesIsStoredOnce() {
        // "a relates to b" and "b relates to a" are one relationship, stored from the smaller id to the larger
        ConceptGraphModel model = ConceptGraphModel.from(List.of(
                note("b", List.of(), List.of("a"), List.of()),
                note("a", List.of(), List.of("b"), List.of())));
        assertEquals(List.of(new Edge(EdgeType.RELATED_TO, "a", "b")), model.edges());
    }

    @Test
    void directedEdgeKeepsItsDirection() {
        ConceptGraphModel model = ConceptGraphModel.from(List.of(
                note("a", List.of("b"), List.of(), List.of()),
                note("b", List.of(), List.of(), List.of())));
        assertEquals(new Edge(EdgeType.REQUIRES, "a", "b"), model.edges().get(0));
    }

    @Test
    void unknownReferenceIsReportedWithTheNoteThatMadeIt() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                ConceptGraphModel.from(List.of(note("rag", List.of("embeddngs"), List.of(), List.of()))));
        assertTrue(e.getMessage().contains("rag requires unknown concept 'embeddngs'"), e.getMessage());
    }

    @Test
    void duplicateIdIsRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                ConceptGraphModel.from(List.of(note("rag", List.of(), List.of(), List.of()),
                        note("rag", List.of(), List.of(), List.of()))));
        assertTrue(e.getMessage().contains("duplicate concept id 'rag'"), e.getMessage());
    }

    @Test
    void cycleInRequiresIsRejectedAndNamesTheLoop() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                ConceptGraphModel.from(List.of(
                        note("rag", List.of("graph-rag"), List.of(), List.of()),
                        note("graph-rag", List.of("rag"), List.of(), List.of()))));
        assertTrue(e.getMessage().contains("cycle in REQUIRES: graph-rag -> rag -> graph-rag"), e.getMessage());
    }

    @Test
    void cycleInRelatedIsFineBecauseOnlyRequiresMustBeAcyclic() {
        ConceptGraphModel model = ConceptGraphModel.from(List.of(
                note("a", List.of(), List.of("b"), List.of()),
                note("b", List.of(), List.of("a"), List.of())));
        assertEquals(1, model.edges().size());
    }

    /** The real notes must always pass. If this fails, a note you edited broke the graph. */
    @Test
    void theRealKnowledgeBaseIsAValidGraph() {
        ConceptGraphProperties props = new ConceptGraphProperties(
                null, null, null, null, false, null, 0, "../knowledge-base", null, null, null, null, null);
        List<ConceptNote> notes = new NoteLoader(props).loadAll();
        ConceptGraphModel model = ConceptGraphModel.from(notes);
        assertEquals(notes.size(), model.concepts().size());
        assertTrue(model.edges().stream().anyMatch(e -> e.type() == EdgeType.REQUIRES));
    }
}
