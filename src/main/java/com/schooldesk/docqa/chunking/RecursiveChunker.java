package com.schooldesk.docqa.chunking;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class RecursiveChunker {

    private static final String[] SEPARATORS = {"\n\n", "\n", ". ", " ", ""};

    private final ChunkingProperties properties;

    RecursiveChunker(ChunkingProperties properties) {
        this.properties = properties;
    }

    public List<Chunk> chunk(String text, int pageNumber, String documentTitle) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<String> rawChunks = split(text.strip(), properties.chunkSize(), properties.overlap());
        List<Chunk> chunks = new ArrayList<>(rawChunks.size());

        for (int i = 0; i < rawChunks.size(); i++) {
            String content = rawChunks.get(i).strip();
            if (content.isBlank()) {
                continue;
            }
            String breadcrumb = documentTitle + " [page " + pageNumber + ", chunk " + (i + 1) + "]";
            String embeddedText = breadcrumb + "\n\n" + content;
            chunks.add(new Chunk(i, pageNumber, content, embeddedText, estimateTokens(content)));
        }
        return chunks;
    }

    private List<String> split(String text, int size, int overlap) {
        if (text.length() <= size) {
            return List.of(text);
        }

        for (String sep : SEPARATORS) {
            List<String> pieces = splitBySeparator(text, sep, size);
            if (pieces.size() > 1) {
                return mergeWithOverlap(pieces, size, overlap);
            }
        }
        return mergeWithOverlap(List.of(text), size, overlap);
    }

    private List<String> splitBySeparator(String text, String sep, int size) {
        if (sep.isEmpty()) {
            List<String> chars = new ArrayList<>();
            for (int i = 0; i < text.length(); i += size) {
                chars.add(text.substring(i, Math.min(i + size, text.length())));
            }
            return chars;
        }

        String[] parts = text.split(java.util.regex.Pattern.quote(sep), -1);
        List<String> pieces = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                pieces.add(part);
            }
        }
        return pieces;
    }

    private List<String> mergeWithOverlap(List<String> pieces, int size, int overlap) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String piece : pieces) {
            if (current.length() + piece.length() > size && current.length() > 0) {
                result.add(current.toString());
                int overlapStart = Math.max(0, current.length() - overlap);
                current = new StringBuilder(current.substring(overlapStart));
            }
            if (current.length() > 0) {
                current.append(" ");
            }
            current.append(piece);
        }

        if (!current.toString().isBlank()) {
            result.add(current.toString());
        }
        return result;
    }

    private int estimateTokens(String text) {
        return (int) Math.ceil(text.length() / 4.0);
    }
}
