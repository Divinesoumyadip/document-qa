package com.schooldesk.docqa.chat;

import java.util.List;
import java.util.UUID;

public record ChatResponse(
        UUID conversationId,
        String answer,
        boolean grounded,
        List<SourceDto> sources) {
}
