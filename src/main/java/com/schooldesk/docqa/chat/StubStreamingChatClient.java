package com.schooldesk.docqa.chat;

import java.util.function.Consumer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "docqa.ai.provider", havingValue = "stub", matchIfMissing = true)
public class StubStreamingChatClient implements StreamingChatClient {

    private final StubChatClient delegate;

    StubStreamingChatClient(StubChatClient delegate) {
        this.delegate = delegate;
    }

    @Override
    public void stream(String systemPrompt, String userPrompt,
            Consumer<String> onToken, Runnable onComplete, Consumer<Throwable> onError) {
        try {
            String answer = delegate.complete(systemPrompt, userPrompt);
            for (String word : answer.split(" ")) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                onToken.accept(word + " ");
                Thread.sleep(20);
            }
            onComplete.run();
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        catch (Exception ex) {
            onError.accept(ex);
        }
    }
}
