package com.schooldesk.docqa.documents;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunkEntity, UUID> {

    int countByDocumentId(UUID documentId);

    void deleteByDocumentId(UUID documentId);

    @Query(value = """
            SELECT c.* FROM document_chunks c
            WHERE c.tenant_id = :tenantId
              AND (:category IS NULL OR c.category = :category)
            ORDER BY c.embedding <=> CAST(:embedding AS vector)
            LIMIT :topK
            """, nativeQuery = true)
    List<DocumentChunkEntity> findSimilar(
            @Param("tenantId") String tenantId,
            @Param("category") String category,
            @Param("embedding") String embedding,
            @Param("topK") int topK);
}
