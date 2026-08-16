package com.schooldesk.docqa.extraction;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.schooldesk.docqa.ingestion.DocumentType;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

@Component
class DocxTextExtractor implements TextExtractor {

    @Override
    public boolean supports(DocumentType type) {
        return type == DocumentType.DOCX;
    }

    @Override
    public ExtractionResult extract(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file);
             XWPFDocument doc = new XWPFDocument(in);
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {

            String text = extractor.getText().strip();
            return new ExtractionResult(List.of(new ExtractedPage(1, text)));
        }
    }
}
