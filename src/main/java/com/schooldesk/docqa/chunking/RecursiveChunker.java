package com.schooldesk.docqa.chunking;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class RecursiveChunker {

    private static final String[] SEPARATORS = {"\n\n", "\n", ". ", " ", ""};

    private final ChunkingProperties properties;

    public RecursiveChunker(ChunkingProperties properties) {
        this.properties = properties;
    }

    public List<Chunk> chunk(String text, int pageNumber, String documentTitle) {
        return chunk(text, pageNumber, documentTitle, 0);
    }

    /**
     * startIndex lets a caller ingesting a multi-page document keep chunk
     * indices unique across pages. Without this, every page restarts at 0 and
     * the (document_id, chunk_index) unique constraint rejects page 2 onward.
     */
    public List<Chunk> chunk(String text, int pageNumber, String documentTitle, int startIndex) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<String> rawChunks = split(text.strip(), properties.chunkSize(), properties.overlap());
        List<Chunk> chunks = new ArrayList<>(rawChunks.size());

        int index = startIndex;
        for (String raw : rawChunks) {
            String content = raw.strip();
            if (content.isBlank()) {
                continue;
            }
            String breadcrumb = documentTitle + " [page " + pageNumber + ", chunk " + (index + 1) + "]";
            String embeddedText = breadcrumb + "\n\n" + content;
            chunks.add(new Chunk(index, pageNumber, content, embeddedText, estimateTokens(content)));
            index++;
        }
        return chunks;
    }

    private List<String> split(String text, int size, int overlap) {
        return splitRecursive(text, size, overlap, 0);
    }

    /**
     * Genuinely recursive: when a piece still exceeds the target after
     * splitting on the current separator, it is re-split on the next one down
     * rather than passed through whole. The earlier version stopped after one
     * pass, so a single 4000-character paragraph in a document that happened to
     * contain a blank line elsewhere came out as one 4000-character chunk.
     */
    private List<String> splitRecursive(String text, int size, int overlap, int separatorIndex) {
        if (text.length() <= size || separatorIndex >= SEPARATORS.length) {
            return List.of(text);
        }

        String separator = SEPARATORS[separatorIndex];
        List<String> pieces = splitBySeparator(text, separator, size);

        if (pieces.size() <= 1) {
            return splitRecursive(text, size, overlap, separatorIndex + 1);
        }

        List<String> resolved = new ArrayList<>();
        for (String piece : pieces) {
            if (piece.length() > size) {
                resolved.addAll(splitRecursive(piece, size, overlap, separatorIndex + 1));
            }
            else {
                resolved.add(piece);
            }
        }

        return mergeWithOverlap(resolved, size, overlap, separator);
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

    private List<String> mergeWithOverlap(List<String> pieces, int size, int overlap,
            String separator) {
        String joiner = separator.isEmpty() ? "" : separator;
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String piece : pieces) {
            if (current.length() + piece.length() > size && current.length() > 0) {
                result.add(current.toString());
                int overlapStart = Math.max(0, current.length() - overlap);
                current = new StringBuilder(current.substring(overlapStart));
            }
            if (current.length() > 0) {
                current.append(joiner);
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
