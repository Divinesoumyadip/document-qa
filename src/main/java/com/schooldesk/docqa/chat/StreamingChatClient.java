package com.schooldesk.docqa.chat;

import java.util.function.Consumer;

public interface StreamingChatClient {

    void stream(String systemPrompt, String userPrompt,
                Consumer<String> onToken,
                Runnable onComplete,
                Consumer<Throwable> onError);
}
