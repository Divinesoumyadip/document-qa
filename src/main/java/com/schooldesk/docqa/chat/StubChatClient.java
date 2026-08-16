package com.schooldesk.docqa.chat;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "docqa.ai.provider", havingValue = "stub", matchIfMissing = true)
public class StubChatClient implements ChatClient {

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        int contextStart = userPrompt.indexOf("CONTEXT:");
        if (contextStart < 0) {
            return "No context was supplied.";
        }
        String context = userPrompt.substring(contextStart + 8).strip();
        String firstLine = context.lines()
                .filter(l -> !l.isBlank() && !l.startsWith("["))
                .findFirst()
                .orElse("");
        return "Based on the available documents: " + firstLine;
    }
}
