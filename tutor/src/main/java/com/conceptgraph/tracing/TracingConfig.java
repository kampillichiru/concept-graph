package com.conceptgraph.tracing;

import com.conceptgraph.config.ConceptGraphProperties;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.api.common.AttributeKey;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * Builds the OpenTelemetry SDK. Every finished span goes to every exporter below:
 *  1. RecentSpanStore: in memory, for the /poc04 endpoints.
 *  2. JsonlSpanExporter: the offline trace file (always on).
 *  3. OTLP to LangSmith: only when LANGSMITH_API_KEY is set.
 * Without a key, nothing leaves the laptop.
 */
@Configuration
public class TracingConfig {

    @Bean
    RecentSpanStore recentSpanStore() {
        return new RecentSpanStore();
    }

    @Bean
    OpenTelemetrySdk openTelemetry(ConceptGraphProperties props, RecentSpanStore recent) {
        Resource resource = Resource.getDefault().merge(
                Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), "conceptgraph-tutor")));

        SdkTracerProviderBuilder tracerProvider = SdkTracerProvider.builder()
                .setResource(resource)
                // Simple = export each span as soon as it ends. Easy to observe; fine for a single laptop.
                .addSpanProcessor(SimpleSpanProcessor.create(recent))
                .addSpanProcessor(SimpleSpanProcessor.create(new JsonlSpanExporter(Path.of(props.traceFile()))));

        if (!props.langsmithApiKey().isBlank()) {
            // Batch = send spans in groups from a background thread. What a real service would use.
            OtlpHttpSpanExporter langsmith = OtlpHttpSpanExporter.builder()
                    .setEndpoint(props.langsmithOtlpEndpoint())
                    .addHeader("x-api-key", props.langsmithApiKey())
                    .addHeader("Langsmith-Project", props.langsmithProject())
                    .build();
            tracerProvider.addSpanProcessor(BatchSpanProcessor.builder(langsmith).build());
        }
        return OpenTelemetrySdk.builder().setTracerProvider(tracerProvider.build()).build();
    }

    @Bean
    Tracer tracer(OpenTelemetrySdk openTelemetry) {
        return openTelemetry.getTracer("conceptgraph");
    }
}
