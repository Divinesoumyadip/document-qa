package com.schooldesk.docqa.ingestion;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;

import com.schooldesk.docqa.documents.DocumentEntity;
import com.schooldesk.docqa.documents.DocumentRepository;
import com.schooldesk.docqa.tenancy.TenantContext;
import com.schooldesk.docqa.web.EmptyUploadException;
import com.schooldesk.docqa.web.IngestionCapacityException;
import com.schooldesk.docqa.web.UnsupportedDocumentTypeException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentUploadService {

    private static final Logger log = LoggerFactory.getLogger(DocumentUploadService.class);

    private final StagingArea stagingArea;
    private final ContentTypeSniffer sniffer;
    private final DocumentRepository documents;
    private final ThreadPoolTaskExecutor ingestionExecutor;
    private final IngestionProperties properties;
    private final IngestionPipeline pipeline;

    DocumentUploadService(StagingArea stagingArea, ContentTypeSniffer sniffer,
            DocumentRepository documents, ThreadPoolTaskExecutor ingestionExecutor,
            IngestionProperties properties, IngestionPipeline pipeline) {
        this.stagingArea = stagingArea;
        this.sniffer = sniffer;
        this.documents = documents;
        this.ingestionExecutor = ingestionExecutor;
        this.properties = properties;
        this.pipeline = pipeline;
    }

    public UploadOutcome accept(MultipartFile upload, String title, String category) {
        if (upload.isEmpty()) {
            throw new EmptyUploadException();
        }

        String tenantId = TenantContext.require();
        StagingArea.StagedFile staged = stage(upload);

        try {
            DocumentType type = sniff(staged.path());
            String filename = safeFilename(upload);
            return register(tenantId, staged, type, resolveTitle(title, filename), filename, category);
        } catch (RuntimeException | Error failure) {
            stagingArea.discard(staged.path());
            throw failure;
        }
    }

    private UploadOutcome register(String tenantId, StagingArea.StagedFile staged, DocumentType type,
            String title, String filename, String category) {

        DocumentEntity document = DocumentEntity.processing(
                tenantId, title, category, filename, staged.sha256(), staged.sizeBytes());

        try {
            documents.saveAndFlush(document);
        } catch (DataIntegrityViolationException duplicate) {
            stagingArea.discard(staged.path());
            return existingDocument(tenantId, staged.sha256());
        }

        submit(document, staged, type);
        return new UploadOutcome(document, false);
    }

    private void submit(DocumentEntity document, StagingArea.StagedFile staged, DocumentType type) {
        try {
            ingestionExecutor.execute(() -> {
                try {
                    pipeline.ingest(document.getId(), staged.path(), type);
                } finally {
                    stagingArea.discard(staged.path());
                }
            });
        } catch (RejectedExecutionException queueFull) {
            documents.delete(document);
            stagingArea.discard(staged.path());
            throw new IngestionCapacityException(properties.retryAfterSeconds());
        }
    }

    private UploadOutcome existingDocument(String tenantId, String sha256) {
        DocumentEntity existing = documents.findByTenantIdAndContentHash(tenantId, sha256)
                .orElseThrow(() -> new IllegalStateException(
                        "Unique violation on content hash but no row found"));
        return new UploadOutcome(existing, true);
    }

    private DocumentType sniff(Path staged) {
        try {
            return sniffer.sniff(staged)
                    .orElseThrow(UnsupportedDocumentTypeException::new);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private StagingArea.StagedFile stage(MultipartFile upload) {
        try {
            return stagingArea.stage(upload);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private String resolveTitle(String title, String filename) {
        return title != null && !title.isBlank() ? title.strip() : filename;
    }

    private String safeFilename(MultipartFile upload) {
        String filename = upload.getOriginalFilename();
        if (filename == null || filename.isBlank()) return "untitled";
        return Path.of(filename.replace('\\', '/')).getFileName().toString();
    }

    public record UploadOutcome(DocumentEntity document, boolean duplicate) {
    }
}
