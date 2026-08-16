package com.schooldesk.docqa.observability;

import com.schooldesk.docqa.embedding.EmbeddingClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("modelProvider")
public class ModelProviderHealthIndicator implements HealthIndicator {

    private final EmbeddingClient embeddingClient;
    private final String provider;

    ModelProviderHealthIndicator(EmbeddingClient embeddingClient,
            @Value("${docqa.ai.provider:stub}") String provider) {
        this.embeddingClient = embeddingClient;
        this.provider = provider;
    }

    @Override
    public Health health() {
        if ("stub".equals(provider)) {
            return Health.up()
                    .withDetail("provider", "stub")
                    .withDetail("note", "deterministic local embeddings, no API key required")
                    .withDetail("dimensions", embeddingClient.dimensions())
                    .build();
        }

        try {
            long start = System.currentTimeMillis();
            embeddingClient.embed(List.of("health check"));
            long elapsed = System.currentTimeMillis() - start;

            return Health.up()
                    .withDetail("provider", provider)
                    .withDetail("dimensions", embeddingClient.dimensions())
                    .withDetail("latencyMs", elapsed)
                    .build();
        }
        catch (Exception ex) {
            return Health.down()
                    .withDetail("provider", provider)
                    .withDetail("error", ex.getClass().getSimpleName())
                    .build();
        }
    }
}
