package com.schooldesk.docqa.ingestion;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import com.schooldesk.docqa.AbstractPostgresIT;
import com.schooldesk.docqa.chunking.Chunk;
import com.schooldesk.docqa.chunking.RecursiveChunker;
import com.schooldesk.docqa.chunking.ChunkingProperties;
import com.schooldesk.docqa.documents.DocumentChunkRepository;
import com.schooldesk.docqa.documents.DocumentEntity;
import com.schooldesk.docqa.documents.DocumentRepository;
import com.schooldesk.docqa.documents.DocumentStatus;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the bug where every page's chunks restarted at index 0: a real
 * multi-page PDF collided against the (document_id, chunk_index) unique
 * constraint and ingestion died on page two of every document. The single
 * text-file fixture used elsewhere never exercised this because it only ever
 * produces one page.
 */
@SpringBootTest
class MultiPageIngestionIT extends AbstractPostgresIT {

    @Autowired
    private IngestionPipeline pipeline;

    @Autowired
    private DocumentRepository documents;

    @Autowired
    private DocumentChunkRepository chunks;

    @TempDir
    Path tempDir;

    @Test
    void ingestsAThreePagePdfWithoutChunkIndexCollisions() throws Exception {
        Path pdf = tempDir.resolve("multi-page.pdf");
        writeThreePagePdf(pdf);

        DocumentEntity doc = DocumentEntity.processing(
                "tenant-multipage", "Multi Page Policy", "FEES", "multi-page.pdf",
                "multipagehash" + "0".repeat(50), Files.size(pdf));
        documents.saveAndFlush(doc);

        pipeline.ingest(doc.getId(), pdf, DocumentType.PDF);

        DocumentEntity updated = documents.findById(doc.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(DocumentStatus.READY);
        assertThat(updated.getChunkCount()).isGreaterThan(0);
        assertThat(chunks.countByDocumentId(doc.getId())).isEqualTo(updated.getChunkCount());
    }

    @Test
    void chunkerAssignsUniqueIndicesAcrossSeparateChunkCallsGivenAStartOffset() {
        RecursiveChunker chunker = new RecursiveChunker(new ChunkingProperties(50, 10, 5, java.util.Map.of("stub", 0.5)));

        List<Chunk> page1 = chunker.chunk("A".repeat(200), 1, "Doc", 0);
        List<Chunk> page2 = chunker.chunk("B".repeat(200), 2, "Doc", page1.size());

        assertThat(page1.get(0).index()).isZero();
        assertThat(page2.get(0).index()).isEqualTo(page1.size());

        long distinctIndices = java.util.stream.Stream.concat(page1.stream(), page2.stream())
                .map(Chunk::index)
                .distinct()
                .count();
        assertThat(distinctIndices).isEqualTo(page1.size() + page2.size());
    }

    private void writeThreePagePdf(Path target) throws Exception {
        try (PDDocument pdf = new PDDocument()) {
            for (int i = 1; i <= 3; i++) {
                PDPage page = new PDPage();
                pdf.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(50, 700);
                    content.showText("Page " + i + " of the fee policy. The late fee for term "
                            + i + " is Rs " + (i * 500) + ". Payment is due by the 10th.");
                    content.endText();
                }
            }
            pdf.save(target.toFile());
        }
    }
}
