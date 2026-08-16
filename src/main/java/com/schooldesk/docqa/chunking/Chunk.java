package com.schooldesk.docqa.chunking;

public record Chunk(
        int index,
        int pageNumber,
        String content,
        String embeddedText,
        int tokenCount) {
}
