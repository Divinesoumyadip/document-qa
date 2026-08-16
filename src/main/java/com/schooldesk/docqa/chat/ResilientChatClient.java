package com.schooldesk.docqa.chat;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.schooldesk.docqa.web.ModelProviderException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class ResilientChatClient implements ChatClient {

    private static final Logger log = LoggerFactory.getLogger(ResilientChatClient.class);

    private static final int MAX_ATTEMPTS = 3;
    private static final long BASE_BACKOFF_MS = 200;
    private static final int FAILURE_THRESHOLD = 5;
    private static final Duration OPEN_DURATION = Duration.ofSeconds(30);

    private final ChatClient delegate;

    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicReference<Instant> openedAt = new AtomicReference<>();

    ResilientChatClient(ChatClient delegate) {
        this.delegate = delegate;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        if (circuitOpen()) {
            log.warn("Circuit breaker open, rejecting call without contacting provider");
            throw new ModelProviderException();
        }

        Exception lastFailure = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                String result = delegate.complete(systemPrompt, userPrompt);
                consecutiveFailures.set(0);
                openedAt.set(null);
                return result;
            }
            catch (Exception ex) {
                lastFailure = ex;
                log.warn("Model call failed attempt={}/{} reason={}",
                        attempt, MAX_ATTEMPTS, ex.getClass().getSimpleName());

                if (attempt < MAX_ATTEMPTS) {
                    sleepBackoff(attempt);
                }
            }
        }

        if (consecutiveFailures.incrementAndGet() >= FAILURE_THRESHOLD) {
            openedAt.set(Instant.now());
            log.error("Circuit breaker opened after {} consecutive failures", FAILURE_THRESHOLD);
        }

        log.error("Model call exhausted retries", lastFailure);
        throw new ModelProviderException();
    }

    private boolean circuitOpen() {
        Instant opened = openedAt.get();
        if (opened == null) {
            return false;
        }
        if (Duration.between(opened, Instant.now()).compareTo(OPEN_DURATION) > 0) {
            openedAt.set(null);
            consecutiveFailures.set(0);
            log.info("Circuit breaker half-open, allowing a probe call");
            return false;
        }
        return true;
    }

    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep(BASE_BACKOFF_MS * (long) Math.pow(2, attempt - 1));
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ModelProviderException();
        }
    }
}
