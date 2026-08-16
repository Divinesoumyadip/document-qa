package com.schooldesk.docqa.documents;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {

    Optional<DocumentEntity> findByIdAndTenantId(UUID id, String tenantId);

    Optional<DocumentEntity> findByTenantIdAndContentHash(String tenantId, String contentHash);

    Page<DocumentEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);

    @Query("""
            select d from DocumentEntity d
            where d.status = com.schooldesk.docqa.documents.DocumentStatus.PROCESSING
              and d.updatedAt < :cutoff
            """)
    List<DocumentEntity> findStaleProcessing(@Param("cutoff") Instant cutoff);

    @Modifying
    int deleteByIdAndTenantId(UUID id, String tenantId);
}
