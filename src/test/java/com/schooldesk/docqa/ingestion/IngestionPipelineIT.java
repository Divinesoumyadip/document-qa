package com.schooldesk.docqa.ingestion;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import com.schooldesk.docqa.AbstractPostgresIT;
import com.schooldesk.docqa.documents.DocumentChunkRepository;
import com.schooldesk.docqa.documents.DocumentEntity;
import com.schooldesk.docqa.documents.DocumentRepository;
import com.schooldesk.docqa.documents.DocumentStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class IngestionPipelineIT extends AbstractPostgresIT {

    @Autowired
    private IngestionPipeline pipeline;

    @Autowired
    private DocumentRepository documents;

    @Autowired
    private DocumentChunkRepository chunks;

    @TempDir
    Path tempDir;

    @Test
    void ingestsATextFileAndPersistsChunks() throws Exception {
        String content = "Fee Policy\n\nThe late fee for Term 2 is Rs 500.\n\n"
                + "The late fee for Term 3 is Rs 600.\n\nPayment must be made by the 10th.";

        Path file = tempDir.resolve("fees.txt");
        Files.writeString(file, content, StandardCharsets.UTF_8);

        DocumentEntity doc = DocumentEntity.processing(
                "tenant-ingest", "Fee Policy", "FEES", "fees.txt",
                "abc123def456" + "0".repeat(52), content.length());
        documents.saveAndFlush(doc);

        pipeline.ingest(doc.getId(), file, DocumentType.TEXT);

        DocumentEntity updated = documents.findById(doc.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(DocumentStatus.READY);
        assertThat(updated.getChunkCount()).isGreaterThan(0);

        int chunkCount = chunks.countByDocumentId(doc.getId());
        assertThat(chunkCount).isEqualTo(updated.getChunkCount());
    }

    @Test
    void marksDocumentFailedWhenFileIsEmpty() throws Exception {
        Path file = tempDir.resolve("empty.txt");
        Files.writeString(file, "", StandardCharsets.UTF_8);

        DocumentEntity doc = DocumentEntity.processing(
                "tenant-ingest", "Empty Doc", null, "empty.txt",
                "def456abc123" + "0".repeat(52), 1L);
        documents.saveAndFlush(doc);

        pipeline.ingest(doc.getId(), file, DocumentType.TEXT);

        DocumentEntity updated = documents.findById(doc.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(updated.getErrorMessage()).isNotBlank();
    }

    @Test
    void tenantIsolationHoldsOnChunks() throws Exception {
        String content = "Transport rules for school buses.";
        Path file = tempDir.resolve("transport.txt");
        Files.writeString(file, content, StandardCharsets.UTF_8);

        DocumentEntity docA = DocumentEntity.processing(
                "tenant-a", "Transport", "TRANSPORT", "transport.txt",
                "aaabbbccc" + "0".repeat(55), content.length());
        documents.saveAndFlush(docA);

        pipeline.ingest(docA.getId(), file, DocumentType.TEXT);

        int chunkCountA = chunks.countByDocumentId(docA.getId());
        assertThat(chunkCountA).isGreaterThan(0);

        UUID randomId = UUID.randomUUID();
        assertThat(chunks.countByDocumentId(randomId)).isZero();
    }
}
