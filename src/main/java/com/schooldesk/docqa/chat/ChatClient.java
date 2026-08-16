package com.schooldesk.docqa.chat;

public interface ChatClient {

    String complete(String systemPrompt, String userPrompt);
}
