package com.schooldesk.docqa.ingestion;

import java.nio.file.Path;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "docqa.ingestion")
public record IngestionProperties(

        @DefaultValue("20MB") @NotNull DataSize maxFileSize,

        @DefaultValue("${java.io.tmpdir}/docqa-staging") @NotNull Path stagingDirectory,

        @DefaultValue("2") @Min(1) @Max(16) int workerThreads,

        @DefaultValue("50") @Min(1) @Max(1000) int queueCapacity,

        @DefaultValue("60") @Min(1) int retryAfterSeconds,

        @DefaultValue("30") @Min(1) int orphanTimeoutMinutes) {
}
