package com.schooldesk.docqa.extraction;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.schooldesk.docqa.ingestion.DocumentType;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
class PdfTextExtractor implements TextExtractor {

    @Override
    public boolean supports(DocumentType type) {
        return type == DocumentType.PDF;
    }

    @Override
    public ExtractionResult extract(Path file) throws IOException {
        try (PDDocument doc = Loader.loadPDF(file.toFile())) {
            List<ExtractedPage> pages = new ArrayList<>(doc.getNumberOfPages());
            PDFTextStripper stripper = new PDFTextStripper();

            for (int i = 1; i <= doc.getNumberOfPages(); i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String text = stripper.getText(doc).strip();
                pages.add(new ExtractedPage(i, text));
            }
            return new ExtractionResult(pages);
        }
    }
}
