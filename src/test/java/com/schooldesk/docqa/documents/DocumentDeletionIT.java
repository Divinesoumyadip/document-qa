package com.schooldesk.docqa.documents;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import com.schooldesk.docqa.AbstractPostgresIT;
import com.schooldesk.docqa.chat.ChatRequest;
import com.schooldesk.docqa.chat.ChatResponse;
import com.schooldesk.docqa.chat.ChatService;
import com.schooldesk.docqa.ingestion.DocumentType;
import com.schooldesk.docqa.ingestion.IngestionPipeline;
import com.schooldesk.docqa.tenancy.TenantContext;
import com.schooldesk.docqa.web.DocumentNotFoundException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class DocumentDeletionIT extends AbstractPostgresIT {

    @Autowired
    private DocumentQueryService queries;

    @Autowired
    private ChatService chatService;

    @Autowired
    private IngestionPipeline pipeline;

    @Autowired
    private DocumentRepository documents;

    @Autowired
    private DocumentChunkRepository chunks;

    @TempDir
    Path tempDir;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void deletingADocumentRemovesItsChunksAndStopsCitations() throws Exception {
        String content = "The annual sports day fee is Rs 250 per student.";
        UUID docId = ingest("tenant-del", "Sports Policy", "FEES", "sports.txt", content, "delA");

        TenantContext.set("tenant-del");

        ChatResponse before = chatService.ask(
                new ChatRequest(null, "annual sports day fee student", null));
        assertThat(before.grounded()).isTrue();
        assertThat(chunks.countByDocumentId(docId)).isGreaterThan(0);

        queries.delete(docId);

        assertThat(chunks.countByDocumentId(docId)).isZero();

        ChatResponse after = chatService.ask(
                new ChatRequest(null, "annual sports day fee student", null));
        assertThat(after.grounded()).isFalse();
        assertThat(after.sources()).isEmpty();
    }

    @Test
    void cannotDeleteAnotherTenantsDocument() throws Exception {
        String content = "Confidential staff salary bands.";
        UUID docId = ingest("tenant-owner-del", "Salary", "HR", "salary.txt", content, "delB");

        TenantContext.set("tenant-other-del");

        assertThatThrownBy(() -> queries.delete(docId))
                .isInstanceOf(DocumentNotFoundException.class);

        assertThat(chunks.countByDocumentId(docId)).isGreaterThan(0);
    }

    @Test
    void listOnlyReturnsOwnTenantDocuments() throws Exception {
        ingest("tenant-list-a", "Doc A", null, "a.txt", "Content for tenant A.", "listA");
        ingest("tenant-list-b", "Doc B", null, "b.txt", "Content for tenant B.", "listB");

        TenantContext.set("tenant-list-a");
        var page = queries.list(org.springframework.data.domain.PageRequest.of(0, 50));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).title()).isEqualTo("Doc A");
    }

    private UUID ingest(String tenantId, String title, String category, String filename,
            String content, String hashSeed) throws Exception {
        Path file = tempDir.resolve(filename);
        Files.writeString(file, content, StandardCharsets.UTF_8);

        String hash = hashSeed + "0".repeat(64 - hashSeed.length());
        DocumentEntity doc = DocumentEntity.processing(
                tenantId, title, category, filename, hash, content.length());
        documents.saveAndFlush(doc);

        pipeline.ingest(doc.getId(), file, DocumentType.TEXT);
        return doc.getId();
    }
}
