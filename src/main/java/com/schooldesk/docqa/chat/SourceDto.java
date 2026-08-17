package com.schooldesk.docqa.chat;

import java.util.List;
import java.util.UUID;

import com.schooldesk.docqa.retrieval.RetrievedChunk;

public record SourceDto(
        UUID documentId,
        String documentTitle,
        Integer pageNumber,
        double similarityScore,
        String snippet) {

    private static final int SNIPPET_LIMIT = 300;

    /** One mapping used by both the streaming and non-streaming endpoints. */
    public static List<SourceDto> from(List<RetrievedChunk> chunks) {
        return chunks.stream().map(SourceDto::from).toList();
    }

    public static SourceDto from(RetrievedChunk chunk) {
        return new SourceDto(
                chunk.documentId(),
                chunk.documentTitle(),
                chunk.pageNumber(),
                Math.round(chunk.similarity() * 10000) / 10000.0,
                truncate(chunk.content()));
    }

    private static String truncate(String content) {
        return content.length() <= SNIPPET_LIMIT
                ? content
                : content.substring(0, SNIPPET_LIMIT) + "...";
    }
}
