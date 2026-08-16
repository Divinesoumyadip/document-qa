package com.schooldesk.docqa.documents;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "documents")
public class DocumentEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @jakarta.persistence.Column(nullable = false)
    private String title;

    private String category;

    @jakarta.persistence.Column(nullable = false)
    private String filename;

    @Column(name = "content_hash", nullable = false, updatable = false)
    private String contentHash;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DocumentEntity() {
    }

    public static DocumentEntity processing(String tenantId, String title, String category,
            String filename, String contentHash, long sizeBytes) {
        DocumentEntity document = new DocumentEntity();
        document.id = UUID.randomUUID();
        document.tenantId = tenantId;
        document.title = title;
        document.category = category;
        document.filename = filename;
        document.contentHash = contentHash;
        document.sizeBytes = sizeBytes;
        document.status = DocumentStatus.PROCESSING;
        document.chunkCount = 0;
        document.createdAt = Instant.now();
        document.updatedAt = document.createdAt;
        return document;
    }

    public void markReady(int chunkCount) {
        this.status = DocumentStatus.READY;
        this.chunkCount = chunkCount;
        this.errorMessage = null;
        this.updatedAt = Instant.now();
    }

    public void markFailed(String reason) {
        this.status = DocumentStatus.FAILED;
        this.errorMessage = reason == null ? "Unknown error"
                : reason.substring(0, Math.min(reason.length(), 1024));
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getTitle() {
        return title;
    }

    public String getCategory() {
        return category;
    }

    public String getFilename() {
        return filename;
    }

    public String getContentHash() {
        return contentHash;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
