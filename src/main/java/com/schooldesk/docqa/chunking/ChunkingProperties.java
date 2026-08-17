package com.schooldesk.docqa.chunking;

import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "docqa.chunking")
public record ChunkingProperties(

        @DefaultValue("700") @Min(100) @Max(4000) int chunkSize,

        @DefaultValue("100") @Min(0) @Max(500) int overlap,

        @DefaultValue("5") @Min(1) @Max(50) int defaultTopK,

        /**
         * Per-provider thresholds. A single global value is dangerous here: the
         * stub scores on bag-of-words overlap and OpenAI on semantic distance,
         * so a number tuned for one silently disarms the refusal path on the
         * other. Switching provider must switch the threshold with it.
         */
        @DefaultValue Map<String, Double> similarityThresholds) {

    private static final double FALLBACK_THRESHOLD = 0.65;

    public double thresholdFor(String provider) {
        Double configured = similarityThresholds.get(provider);
        if (configured != null) {
            return configured;
        }
        // An unknown provider gets the stricter semantic default rather than
        // the permissive stub value: refusing too often is a visible bug,
        // answering when it shouldn't is a silent one.
        return FALLBACK_THRESHOLD;
    }
}
