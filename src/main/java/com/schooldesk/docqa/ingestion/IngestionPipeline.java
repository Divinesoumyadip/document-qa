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
import com.schooldesk.docqa.documents.DocumentChunkRepository;
import com.schooldesk.docqa.documents.DocumentEntity;
import com.schooldesk.docqa.documents.DocumentRepository;
import com.schooldesk.docqa.embedding.EmbeddingClient;
import com.schooldesk.docqa.extraction.DocumentExtractorService;
import com.schooldesk.docqa.extraction.ExtractionResult;
import com.schooldesk.docqa.extraction.ExtractedPage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestionPipeline {

    private static final Logger log = LoggerFactory.getLogger(IngestionPipeline.class);

    private final DocumentExtractorService extractor;
    private final RecursiveChunker chunker;
    private final EmbeddingClient embeddingClient;
    private final DocumentChunkRepository chunks;
    private final ChunkWriter chunkWriter;
    private final DocumentRepository documents;

    IngestionPipeline(DocumentExtractorService extractor, RecursiveChunker chunker,
            EmbeddingClient embeddingClient, DocumentChunkRepository chunks,
            DocumentRepository documents, ChunkWriter chunkWriter) {
        this.extractor = extractor;
        this.chunker = chunker;
        this.embeddingClient = embeddingClient;
        this.chunks = chunks;
        this.documents = documents;
        this.chunkWriter = chunkWriter;
    }

    @Transactional
    public void ingest(UUID documentId, Path stagedFile, DocumentType type) {
        DocumentEntity document = documents.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));

        try {
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
                    allChunks.addAll(chunker.chunk(page.text(), page.pageNumber(), document.getTitle()));
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

        } catch (IOException ex) {
            log.error("Extraction failed documentId={}", documentId, ex);
            document.markFailed(ex.getMessage());
            documents.save(document);
            throw new UncheckedIOException(ex);
        } catch (Exception ex) {
            log.error("Ingestion failed documentId={}", documentId, ex);
            document.markFailed(ex.getMessage());
            documents.save(document);
            throw ex;
        }
    }
}
