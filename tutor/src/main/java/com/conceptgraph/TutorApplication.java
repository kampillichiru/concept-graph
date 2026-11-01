package com.conceptgraph;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * The one main class. Start it, then open http://localhost:8080/swagger-ui.html
 * See docs/lessons/poc01.md.
 */
@SpringBootApplication
@OpenAPIDefinition(info = @Info(
        title = "ConceptGraph Tutor",
        version = "POC 1",
        description = "Lesson endpoints for learning AI concepts with LangChain4j and a local Ollama model. "
                + "Calls take seconds on a CPU laptop; watch the app console for llm-trace lines."))
@ConfigurationPropertiesScan
public class TutorApplication {

    public static void main(String[] args) {
        SpringApplication.run(TutorApplication.class, args);
    }
}
