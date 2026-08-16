package com.schooldesk.docqa.chat;

import java.util.concurrent.atomic.AtomicInteger;

import com.schooldesk.docqa.web.ModelProviderException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResilientChatClientTest {

    @Test
    void returnsResultWhenDelegateSucceedsFirstTime() {
        ResilientChatClient client = new ResilientChatClient(
                (system, user) -> "answer");

        assertThat(client.complete("s", "u")).isEqualTo("answer");
    }

    @Test
    void retriesAndSucceedsOnSecondAttempt() {
        AtomicInteger calls = new AtomicInteger();
        ResilientChatClient client = new ResilientChatClient((system, user) -> {
            if (calls.incrementAndGet() < 2) {
                throw new RuntimeException("transient");
            }
            return "recovered";
        });

        assertThat(client.complete("s", "u")).isEqualTo("recovered");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void throwsServiceUnavailableAfterExhaustingRetries() {
        AtomicInteger calls = new AtomicInteger();
        ResilientChatClient client = new ResilientChatClient((system, user) -> {
            calls.incrementAndGet();
            throw new RuntimeException("provider down");
        });

        assertThatThrownBy(() -> client.complete("s", "u"))
                .isInstanceOf(ModelProviderException.class);

        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void neverLeaksProviderDetailToTheClient() {
        ResilientChatClient client = new ResilientChatClient((system, user) -> {
            throw new RuntimeException("api-key REDACTED-TOKEN-abc123 rejected by upstream");
        });

        assertThatThrownBy(() -> client.complete("s", "u"))
                .isInstanceOf(ModelProviderException.class)
                .hasMessageNotContaining("REDACTED-TOKEN")
                .hasMessageContaining("temporarily unavailable");
    }
}
