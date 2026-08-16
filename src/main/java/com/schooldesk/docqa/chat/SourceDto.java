package com.schooldesk.docqa.chat;

import java.util.UUID;

public record SourceDto(
        UUID documentId,
        String documentTitle,
        Integer pageNumber,
        double similarityScore,
        String snippet) {
}
