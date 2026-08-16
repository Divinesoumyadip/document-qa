package com.schooldesk.docqa.extraction;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import com.schooldesk.docqa.ingestion.DocumentType;

import org.springframework.stereotype.Service;

@Service
public class DocumentExtractorService {

    private final List<TextExtractor> extractors;

    DocumentExtractorService(List<TextExtractor> extractors) {
        this.extractors = extractors;
    }

    public ExtractionResult extract(Path file, DocumentType type) throws IOException {
        return extractors.stream()
                .filter(e -> e.supports(type))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No extractor for " + type))
                .extract(file);
    }
}
