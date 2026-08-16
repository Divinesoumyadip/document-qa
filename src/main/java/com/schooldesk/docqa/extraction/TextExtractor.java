package com.schooldesk.docqa.extraction;

import java.io.IOException;
import java.nio.file.Path;

import com.schooldesk.docqa.ingestion.DocumentType;

public interface TextExtractor {

    boolean supports(DocumentType type);

    ExtractionResult extract(Path file) throws IOException;
}
