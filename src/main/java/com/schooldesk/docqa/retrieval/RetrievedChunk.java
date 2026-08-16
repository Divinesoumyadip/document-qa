package com.schooldesk.docqa.retrieval;

import java.util.UUID;

public record RetrievedChunk(
        UUID chunkId,
        UUID documentId,
        String documentTitle,
        Integer pageNumber,
        String content,
        double similarity) {
}
