package com.conceptgraph.graph;

/** A question named a concept id that is not in the graph. Mapped to HTTP 404 by GraphErrorHandler. */
public class ConceptNotFoundException extends RuntimeException {

    public ConceptNotFoundException(String conceptId) {
        super("No concept with id '" + conceptId + "'. Ids are the note file names, for example 'graph-rag'.");
    }
}
