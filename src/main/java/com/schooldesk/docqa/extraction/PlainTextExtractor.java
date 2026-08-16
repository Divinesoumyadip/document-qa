package com.schooldesk.docqa.extraction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.schooldesk.docqa.ingestion.DocumentType;

import org.springframework.stereotype.Component;

@Component
class PlainTextExtractor implements TextExtractor {

    @Override
    public boolean supports(DocumentType type) {
        return type == DocumentType.TEXT;
    }

    @Override
    public ExtractionResult extract(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8).strip();
        return new ExtractionResult(List.of(new ExtractedPage(1, text)));
    }
}
