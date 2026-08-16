package com.schooldesk.docqa.chat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.schooldesk.docqa.AbstractPostgresIT;
import com.schooldesk.docqa.documents.DocumentEntity;
import com.schooldesk.docqa.documents.DocumentRepository;
import com.schooldesk.docqa.ingestion.DocumentType;
import com.schooldesk.docqa.ingestion.IngestionPipeline;
import com.schooldesk.docqa.tenancy.TenantContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ChatServiceIT extends AbstractPostgresIT {

    @Autowired
    private ChatService chatService;

    @Autowired
    private IngestionPipeline pipeline;

    @Autowired
    private DocumentRepository documents;

    @TempDir
    Path tempDir;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void refusesWhenNothingClearsTheThreshold() {
        TenantContext.set("tenant-empty-corpus");

        ChatResponse response = chatService.ask(
                new ChatRequest(null, "What is the late fee for term 2?", null));

        assertThat(response.grounded()).isFalse();
        assertThat(response.answer()).isEqualTo(ChatService.REFUSAL_MESSAGE);
        assertThat(response.sources()).isEmpty();
        assertThat(response.conversationId()).isNotNull();
    }

    @Test
    void answersWithSourcesWhenChunksAreFound() throws Exception {
        String content = "The late fee for Term 2 is Rs 500 payable by the 10th of the month.";
        ingest("tenant-chat-a", "Fee Policy", "FEES", "fees.txt", content, "chatA");

        TenantContext.set("tenant-chat-a");
        ChatResponse response = chatService.ask(
                new ChatRequest(null, "late fee Term 2", null));

        assertThat(response.grounded()).isTrue();
        assertThat(response.sources()).isNotEmpty();
        assertThat(response.sources().get(0).documentTitle()).isEqualTo("Fee Policy");
        assertThat(response.sources().get(0).similarityScore()).isGreaterThan(0.0);
    }

    @Test
    void tenantCannotRetrieveAnotherTenantsChunks() throws Exception {
        String content = "The confidential HR leave policy allows 24 days per year.";
        ingest("tenant-owner", "HR Policy", "HR", "hr.txt", content, "ownerHash");

        TenantContext.set("tenant-intruder");
        ChatResponse response = chatService.ask(
                new ChatRequest(null, "confidential HR leave policy 24 days", null));

        assertThat(response.grounded()).isFalse();
        assertThat(response.sources()).isEmpty();
        assertThat(response.answer()).isEqualTo(ChatService.REFUSAL_MESSAGE);
    }

    @Test
    void reusesAnExistingConversationWhenIdSupplied() {
        TenantContext.set("tenant-convo");

        ChatResponse first = chatService.ask(new ChatRequest(null, "first question", null));
        ChatResponse second = chatService.ask(
                new ChatRequest(first.conversationId(), "second question", null));

        assertThat(second.conversationId()).isEqualTo(first.conversationId());
    }

    private void ingest(String tenantId, String title, String category, String filename,
            String content, String hashSeed) throws Exception {
        Path file = tempDir.resolve(filename);
        Files.writeString(file, content, StandardCharsets.UTF_8);

        String hash = hashSeed + "0".repeat(64 - hashSeed.length());
        DocumentEntity doc = DocumentEntity.processing(
                tenantId, title, category, filename, hash, content.length());
        documents.saveAndFlush(doc);

        pipeline.ingest(doc.getId(), file, DocumentType.TEXT);
    }
}
