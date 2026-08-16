package com.schooldesk.docqa.documents;

import java.util.UUID;

import com.schooldesk.docqa.tenancy.TenantContext;
import com.schooldesk.docqa.web.DocumentNotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentQueryService {

    private static final Logger log = LoggerFactory.getLogger(DocumentQueryService.class);

    private final DocumentRepository documents;

    DocumentQueryService(DocumentRepository documents) {
        this.documents = documents;
    }

    public Page<DocumentResponse> list(Pageable pageable) {
        String tenantId = TenantContext.require();
        return documents.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable)
                .map(DocumentResponse::from);
    }

    public DocumentResponse get(UUID id) {
        String tenantId = TenantContext.require();
        return documents.findByIdAndTenantId(id, tenantId)
                .map(DocumentResponse::from)
                .orElseThrow(DocumentNotFoundException::new);
    }

    @Transactional
    public void delete(UUID id) {
        String tenantId = TenantContext.require();

        DocumentEntity document = documents.findByIdAndTenantId(id, tenantId)
                .orElseThrow(DocumentNotFoundException::new);

        documents.delete(document);
        log.info("Document deleted documentId={} tenantId={}", id, tenantId);
    }
}
