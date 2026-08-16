package com.schooldesk.docqa.observability;

import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AiMetrics {

    private static final Logger log = LoggerFactory.getLogger(AiMetrics.class);

    private static final double EMBEDDING_COST_PER_MILLION_TOKENS = 0.02;
    private static final double CHAT_INPUT_COST_PER_MILLION = 0.15;
    private static final double CHAT_OUTPUT_COST_PER_MILLION = 0.60;

    private final MeterRegistry registry;

    AiMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordRetrieval(long millis, int candidates, int aboveThreshold) {
        registry.timer("docqa.retrieval.latency").record(millis, TimeUnit.MILLISECONDS);
        registry.counter("docqa.retrieval.candidates").increment(candidates);
        registry.counter("docqa.retrieval.above_threshold").increment(aboveThreshold);
    }

    public void recordModelCall(long millis, int inputTokens, int outputTokens) {
        registry.timer("docqa.model.latency").record(millis, TimeUnit.MILLISECONDS);
        registry.counter("docqa.model.tokens.in").increment(inputTokens);
        registry.counter("docqa.model.tokens.out").increment(outputTokens);

        double cost = (inputTokens / 1_000_000.0) * CHAT_INPUT_COST_PER_MILLION
                + (outputTokens / 1_000_000.0) * CHAT_OUTPUT_COST_PER_MILLION;

        registry.counter("docqa.model.cost.usd").increment(cost);

        log.info("Model call latencyMs={} tokensIn={} tokensOut={} estimatedCostUsd={}",
                millis, inputTokens, outputTokens, String.format("%.6f", cost));
    }

    public void recordEmbedding(long millis, int tokens, int batchSize) {
        registry.timer("docqa.embedding.latency").record(millis, TimeUnit.MILLISECONDS);
        registry.counter("docqa.embedding.tokens").increment(tokens);
        registry.counter("docqa.embedding.batches").increment();

        double cost = (tokens / 1_000_000.0) * EMBEDDING_COST_PER_MILLION_TOKENS;
        registry.counter("docqa.embedding.cost.usd").increment(cost);

        log.info("Embedding batch latencyMs={} tokens={} batchSize={} estimatedCostUsd={}",
                millis, tokens, batchSize, String.format("%.6f", cost));
    }

    public void recordRefusal() {
        registry.counter("docqa.chat.refusals").increment();
    }

    public Timer.Sample start() {
        return Timer.start(registry);
    }
}
