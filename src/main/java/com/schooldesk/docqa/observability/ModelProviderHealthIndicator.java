package com.schooldesk.docqa.observability;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.schooldesk.docqa.embedding.EmbeddingClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("modelProvider")
public class ModelProviderHealthIndicator implements HealthIndicator {

    /**
     * The compose healthcheck polls every 5 seconds. Probing the real provider
     * that often would be roughly 17k billed embedding calls a day, and would
     * also let a slow provider make the container look unhealthy. One live
     * probe per minute is enough to detect an outage.
     */
    private static final Duration PROBE_TTL = Duration.ofSeconds(60);

    private final EmbeddingClient embeddingClient;
    private final String provider;

    private final AtomicReference<CachedProbe> cached = new AtomicReference<>();

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

        CachedProbe last = cached.get();
        if (last != null && Duration.between(last.at(), Instant.now()).compareTo(PROBE_TTL) < 0) {
            return last.health();
        }

        Health probed = probeProvider();
        cached.set(new CachedProbe(Instant.now(), probed));
        return probed;
    }

    private Health probeProvider() {
        try {
            long start = System.currentTimeMillis();
            embeddingClient.embed(List.of("health check"));
            long elapsed = System.currentTimeMillis() - start;

            return Health.up()
                    .withDetail("provider", provider)
                    .withDetail("dimensions", embeddingClient.dimensions())
                    .withDetail("latencyMs", elapsed)
                    .withDetail("probeCachedForSeconds", PROBE_TTL.toSeconds())
                    .build();
        }
        catch (Exception ex) {
            // Only the exception type is exposed; a provider error message can
            // carry key fragments or account detail (NFR-5).
            return Health.down()
                    .withDetail("provider", provider)
                    .withDetail("error", ex.getClass().getSimpleName())
                    .build();
        }
    }

    private record CachedProbe(Instant at, Health health) {
    }
}
