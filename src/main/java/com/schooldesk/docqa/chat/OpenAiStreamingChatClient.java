package com.schooldesk.docqa.chat;

import java.util.List;
import java.util.function.Consumer;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "docqa.ai.provider", havingValue = "openai")
public class OpenAiStreamingChatClient implements StreamingChatClient {

    private final ChatModel chatModel;

    OpenAiStreamingChatClient(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public void stream(String systemPrompt, String userPrompt,
            Consumer<String> onToken, Runnable onComplete, Consumer<Throwable> onError) {

        Prompt prompt = new Prompt(List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(userPrompt)));

        chatModel.stream(prompt)
                .doOnNext(response -> {
                    String token = response.getResult().getOutput().getText();
                    if (token != null && !token.isEmpty()) {
                        onToken.accept(token);
                    }
                })
                .doOnComplete(onComplete)
                .doOnError(onError)
                .blockLast();
    }
}
