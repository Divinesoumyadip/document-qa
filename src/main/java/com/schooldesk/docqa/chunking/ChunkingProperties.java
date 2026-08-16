package com.schooldesk.docqa.chunking;

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

        @DefaultValue("0.65") double similarityThreshold) {
}
