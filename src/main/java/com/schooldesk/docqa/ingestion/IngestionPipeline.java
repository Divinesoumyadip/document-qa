package com.schooldesk.docqa.ingestion;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.schooldesk.docqa.chunking.Chunk;
import com.schooldesk.docqa.chunking.RecursiveChunker;
import com.schooldesk.docqa.documents.ChunkWriter;
import com.schooldesk.docqa.documents.DocumentEntity;
import com.schooldesk.docqa.documents.DocumentRepository;
import com.schooldesk.docqa.embedding.EmbeddingClient;
import com.schooldesk.docqa.extraction.DocumentExtractorService;
import com.schooldesk.docqa.extraction.ExtractedPage;
import com.schooldesk.docqa.extraction.ExtractionResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestionPipeline {

    private static final Logger log = LoggerFactory.getLogger(IngestionPipeline.class);

    private final DocumentExtractorService extractor;
    private final RecursiveChunker chunker;
    private final EmbeddingClient embeddingClient;
    private final ChunkWriter chunkWriter;
    private final DocumentRepository documents;

    IngestionPipeline(DocumentExtractorService extractor, RecursiveChunker chunker,
            EmbeddingClient embeddingClient, ChunkWriter chunkWriter,
            DocumentRepository documents) {
        this.extractor = extractor;
        this.chunker = chunker;
        this.embeddingClient = embeddingClient;
        this.chunkWriter = chunkWriter;
        this.documents = documents;
    }

    public void ingest(UUID documentId, Path stagedFile, DocumentType type) {
        try {
            doIngest(documentId, stagedFile, type);
        }
        catch (Exception ex) {
            // Marking failure happens in its own REQUIRES_NEW transaction,
            // never inside the try block above. If that write shared the
            // failing transaction, the rethrow that follows would roll it
            // back too, and the document would sit on PROCESSING forever
            // instead of reporting FAILED with a reason.
            log.error("Ingestion failed documentId={}", documentId, ex);
            markFailed(documentId, rootMessage(ex));
            if (ex instanceof IOException io) {
                throw new UncheckedIOException(io);
            }
            throw ex instanceof RuntimeException re ? re : new RuntimeException(ex);
        }
    }

    @Transactional
    void doIngest(UUID documentId, Path stagedFile, DocumentType type) throws IOException {
        DocumentEntity document = documents.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));

        log.info("Ingestion starting documentId={} type={}", documentId, type);

        ExtractionResult extracted = extractor.extract(stagedFile, type);
        if (extracted.isEmpty()) {
            document.markFailed("No text content could be extracted.");
            documents.save(document);
            return;
        }

        List<Chunk> allChunks = new ArrayList<>();
        for (ExtractedPage page : extracted.pages()) {
            if (page.hasContent()) {
                // startIndex = allChunks.size() keeps chunk_index unique across
                // pages. Without this, page 2 restarts at 0 and collides with
                // page 1's chunks under the (document_id, chunk_index) constraint.
                List<Chunk> pageChunks = chunker.chunk(
                        page.text(), page.pageNumber(), document.getTitle(), allChunks.size());
                allChunks.addAll(pageChunks);
            }
        }

        if (allChunks.isEmpty()) {
            document.markFailed("Document produced no chunks after splitting.");
            documents.save(document);
            return;
        }

        List<String> embeddedTexts = allChunks.stream().map(Chunk::embeddedText).toList();
        List<float[]> embeddings = embeddingClient.embed(embeddedTexts);

        List<ChunkWriter.ChunkInsert> inserts = new ArrayList<>(allChunks.size());
        for (int i = 0; i < allChunks.size(); i++) {
            Chunk chunk = allChunks.get(i);
            inserts.add(new ChunkWriter.ChunkInsert(
                    document.getId(),
                    document.getTenantId(),
                    document.getCategory(),
                    chunk.index(),
                    chunk.pageNumber(),
                    chunk.content(),
                    chunk.embeddedText(),
                    chunk.tokenCount(),
                    embeddings.get(i)));
        }

        chunkWriter.insertAll(inserts);
        document.markReady(inserts.size());
        documents.save(document);

        log.info("Ingestion complete documentId={} chunks={}", documentId, inserts.size());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void markFailed(UUID documentId, String reason) {
        documents.findById(documentId).ifPresent(document -> {
            document.markFailed(reason);
            documents.save(document);
        });
    }

    private String rootMessage(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
    }
}
