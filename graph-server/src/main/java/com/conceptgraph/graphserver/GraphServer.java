package com.conceptgraph.graphserver;

import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.configuration.connectors.BoltConnector;
import org.neo4j.configuration.connectors.BoltConnectorInternalSettings;
import org.neo4j.configuration.connectors.HttpConnector;
import org.neo4j.configuration.helpers.SocketAddress;
import org.neo4j.harness.Neo4j;
import org.neo4j.harness.Neo4jBuilders;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

/**
 * POC 6: starts Neo4j in this JVM and keeps it running until Ctrl+C.
 *
 * <pre>
 * java -jar graph-server/target/graph-server.jar        (from the repo root)
 * </pre>
 *
 * The graph is NOT kept between starts. The harness gives each start a fresh database folder, so the server always
 * starts empty. That is acceptable here: the graph is derived from knowledge-base/concepts, and
 * POST /poc06/load rebuilds it in about 2 seconds. Run it after every start. Persisting the store across restarts
 * is a known open item (see docs/lessons/poc06.md, "Known limits").
 *
 * Bolt (what the tutor's driver uses) is on localhost:7687. HTTP (Neo4j Browser) is on localhost:7474.
 * Both bind to localhost only, and auth is off: this is a single-user learning server on one laptop.
 * A shared server would turn auth on and use a real password.
 */
public final class GraphServer {

    private GraphServer() {
    }

    public static void main(String[] args) throws InterruptedException {
        int boltPort = Integer.parseInt(env("GRAPH_BOLT_PORT", "7687"));
        int httpPort = Integer.parseInt(env("GRAPH_HTTP_PORT", "7474"));

        // The builder is Neo4j's embedding API. Each withConfig(...) is one line of neo4j.conf.
        Neo4j neo4j = Neo4jBuilders.newInProcessBuilder()
                // Neo4j also starts an in-JVM Bolt channel for embedded callers. Its netty event loop does not
                // support local channels on this JDK (fails at startup). We do not need it: the tutor talks TCP.
                .withConfig(BoltConnectorInternalSettings.enable_local_connector, false)
                .withConfig(BoltConnector.enabled, true)
                .withConfig(BoltConnector.listen_address, new SocketAddress("localhost", boltPort))
                .withConfig(HttpConnector.enabled, true)
                .withConfig(HttpConnector.listen_address, new SocketAddress("localhost", httpPort))
                .withConfig(GraphDatabaseSettings.auth_enabled, false)
                .build();

        // Ctrl+C runs this: Neo4j closes its database cleanly before the JVM exits.
        Runtime.getRuntime().addShutdownHook(new Thread(neo4j::close, "neo4j-shutdown"));

        System.out.println("Neo4j is running (embedded, no Docker)");
        System.out.println("  bolt:  " + neo4j.boltURI());
        System.out.println("  http:  " + neo4j.httpURI() + "   (Neo4j Browser)");
        System.out.println("  The graph is empty after every start: run POST /poc06/load in the tutor.");
        System.out.println("Stop with Ctrl+C.");

        new CountDownLatch(1).await();
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
