package com.conceptgraph.graph;

import org.neo4j.driver.exceptions.ServiceUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns graph failures into clear HTTP errors, for the POC 6 lesson endpoints only.
 * The most common one: graph-server is not running. Without this, you would see a driver stack trace.
 */
@RestControllerAdvice(basePackages = "com.conceptgraph.lessons.poc06")
public class GraphErrorHandler {

    @ExceptionHandler(ConceptNotFoundException.class)
    ProblemDetail notFound(ConceptNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    ProblemDetail graphServerDown(ServiceUnavailableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "graph-server is not reachable (NEO4J_URI, default bolt://localhost:7687). "
                        + "Start it first: java -jar graph-server/target/graph-server.jar  (from the repo root). "
                        + "Driver said: " + e.getMessage());
    }

    /** Invalid notes (unknown ids, cycles, duplicates) from ConceptGraphModel. Nothing was written. */
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalidGraph(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    }
}
