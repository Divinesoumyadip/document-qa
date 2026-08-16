package com.schooldesk.docqa.documents;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "document_chunks")
public class DocumentChunkEntity {

    @Id
    private UUID id;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "embedded_text", nullable = false, columnDefinition = "text")
    private String embeddedText;

    private String category;

    @Column(name = "token_count", nullable = false)
    private int tokenCount;

    @Column(nullable = false, columnDefinition = "vector(1536)")
    private String embedding;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DocumentChunkEntity() {
    }

    public static DocumentChunkEntity of(UUID documentId, String tenantId, String category,
            int chunkIndex, Integer pageNumber, String content, String embeddedText,
            int tokenCount, float[] embedding) {
        DocumentChunkEntity e = new DocumentChunkEntity();
        e.id = UUID.randomUUID();
        e.documentId = documentId;
        e.tenantId = tenantId;
        e.category = category;
        e.chunkIndex = chunkIndex;
        e.pageNumber = pageNumber;
        e.content = content;
        e.embeddedText = embeddedText;
        e.tokenCount = tokenCount;
        e.embedding = toVectorLiteral(embedding);
        e.createdAt = Instant.now();
        return e;
    }

    static String toVectorLiteral(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 8 + 2);
        sb.append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }

    public UUID getId() { return id; }
    public UUID getDocumentId() { return documentId; }
    public String getTenantId() { return tenantId; }
    public int getChunkIndex() { return chunkIndex; }
    public Integer getPageNumber() { return pageNumber; }
    public String getContent() { return content; }
    public String getEmbeddedText() { return embeddedText; }
    public String getCategory() { return category; }
    public int getTokenCount() { return tokenCount; }
    public String getEmbedding() { return embedding; }
    public Instant getCreatedAt() { return createdAt; }
}
