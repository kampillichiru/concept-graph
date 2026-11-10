package com.conceptgraph.graph;

import com.conceptgraph.config.ConceptGraphProperties;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The Neo4j driver: the client that speaks Bolt to graph-server (POC 6). Written out by hand, like LlmConfig,
 * so the wiring stays visible.
 *
 * Creating the driver does not connect. The first query does, so the tutor still starts when graph-server is down,
 * and the query then fails with a clear 503 (see GraphErrorHandler).
 */
@Configuration
public class GraphConfig {

    @Bean(destroyMethod = "close")
    public Driver neo4jDriver(ConceptGraphProperties props) {
        // AuthTokens.none(): graph-server runs with auth off, bound to localhost (see GraphServer.java).
        return GraphDatabase.driver(props.neo4jUri(), AuthTokens.none());
    }
}
