package com.schooldesk.docqa.documents;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        String title,
        String category,
        String filename,
        DocumentStatus status,
        String errorMessage,
        int chunkCount,
        long sizeBytes,
        Instant uploadedAt) {

    public static DocumentResponse from(DocumentEntity document) {
        return new DocumentResponse(
                document.getId(),
                document.getTitle(),
                document.getCategory(),
                document.getFilename(),
                document.getStatus(),
                document.getErrorMessage(),
                document.getChunkCount(),
                document.getSizeBytes(),
                document.getCreatedAt());
    }
}
