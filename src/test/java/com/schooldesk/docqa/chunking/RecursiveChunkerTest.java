package com.schooldesk.docqa.chunking;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecursiveChunkerTest {

    private final RecursiveChunker chunker = new RecursiveChunker(
            new ChunkingProperties(700, 100, 5, 0.65));

    @Test
    void returnsEmptyListForNullText() {
        assertThat(chunker.chunk(null, 1, "Doc")).isEmpty();
    }

    @Test
    void returnsEmptyListForBlankText() {
        assertThat(chunker.chunk("   ", 1, "Doc")).isEmpty();
    }

    @Test
    void returnsSingleChunkForShortText() {
        String text = "The late fee for Term 2 is five hundred rupees.";
        List<Chunk> chunks = chunker.chunk(text, 1, "Fee Policy");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).content()).isEqualTo(text);
        assertThat(chunks.get(0).pageNumber()).isEqualTo(1);
    }

    @Test
    void splitsLongTextIntoMultipleChunks() {
        String paragraph = "A".repeat(300) + "\n\n" + "B".repeat(300) + "\n\n" + "C".repeat(300);
        List<Chunk> chunks = chunker.chunk(paragraph, 2, "Policy");

        assertThat(chunks.size()).isGreaterThan(1);
        chunks.forEach(c -> assertThat(c.content().length()).isLessThanOrEqualTo(800));
    }

    @Test
    void prefixesEmbeddedTextWithBreadcrumb() {
        List<Chunk> chunks = chunker.chunk("Fee is Rs 500 for late payment.", 3, "Fee Policy");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).embeddedText()).startsWith("Fee Policy [page 3");
        assertThat(chunks.get(0).embeddedText()).contains("Fee is Rs 500");
    }

    @Test
    void singleWordFileProducesOneChunk() {
        List<Chunk> chunks = chunker.chunk("Admissions", 1, "SOP");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).content()).isEqualTo("Admissions");
    }

    @Test
    void chunkIndexIsSequential() {
        String text = "X".repeat(800) + "\n\n" + "Y".repeat(800);
        List<Chunk> chunks = chunker.chunk(text, 1, "Doc");

        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).index()).isEqualTo(i);
        }
    }

    @Test
    void estimatesTokenCountAsQuarterOfLength() {
        String text = "A".repeat(400);
        List<Chunk> chunks = chunker.chunk(text, 1, "Doc");

        assertThat(chunks.get(0).tokenCount()).isEqualTo(100);
    }
}
