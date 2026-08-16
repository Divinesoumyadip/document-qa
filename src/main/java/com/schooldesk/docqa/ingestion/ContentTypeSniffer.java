package com.schooldesk.docqa.ingestion;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.metadata.Metadata;
import org.springframework.stereotype.Component;

@Component
public class ContentTypeSniffer {

    private static final Detector DETECTOR = new DefaultDetector();

    private static final String OOXML_DOCUMENT_ENTRY = "word/document.xml";

    public Optional<DocumentType> sniff(Path file) throws IOException {
        String mediaType = detectMediaType(file);

        return switch (mediaType) {
            case "application/pdf" -> Optional.of(DocumentType.PDF);
            case "text/plain" -> Optional.of(DocumentType.TEXT);
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                    Optional.of(DocumentType.DOCX);

            case "application/zip", "application/x-tika-ooxml" ->
                    isWordDocument(file) ? Optional.of(DocumentType.DOCX) : Optional.empty();
            default -> Optional.empty();
        };
    }

    private String detectMediaType(Path file) throws IOException {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {

            return DETECTOR.detect(in, new Metadata()).getBaseType().toString();
        }
    }

    private boolean isWordDocument(Path file) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (OOXML_DOCUMENT_ENTRY.equals(entry.getName())) {
                    return true;
                }
            }
            return false;
        }
        catch (IllegalArgumentException malformedEntryName) {

            return false;
        }
    }
}
