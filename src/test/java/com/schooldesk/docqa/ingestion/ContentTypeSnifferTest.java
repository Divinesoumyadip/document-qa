package com.schooldesk.docqa.ingestion;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class ContentTypeSnifferTest {

    private final ContentTypeSniffer sniffer = new ContentTypeSniffer();

    @TempDir
    Path tempDir;

    @Test
    void identifiesAPdfByItsMagicBytes() throws IOException {
        Path file = write("policy.pdf", "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n".getBytes(StandardCharsets.US_ASCII));

        assertThat(sniffer.sniff(file)).contains(DocumentType.PDF);
    }

    @Test
    void rejectsAnExecutableRenamedToPdf() throws IOException {

        Path file = write("invoice.pdf", new byte[] { 'M', 'Z', (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00 });

        assertThat(sniffer.sniff(file)).isEmpty();
    }

    @Test
    void identifiesADocxByItsInternalDocumentPart() throws IOException {
        Path file = write("leave-policy.docx", zipContaining("word/document.xml"));

        assertThat(sniffer.sniff(file)).contains(DocumentType.DOCX);
    }

    @Test
    void rejectsAPlainZipRenamedToDocx() throws IOException {
        Path file = write("archive.docx", zipContaining("holiday-photos/beach.jpg"));

        assertThat(sniffer.sniff(file)).isEmpty();
    }

    @Test
    void treatsMarkdownAsPlainText() throws IOException {
        Path file = write("fees.md", "# Fee Policy\n\n## 5.2 Late Payment\n".getBytes(StandardCharsets.UTF_8));

        assertThat(sniffer.sniff(file)).contains(DocumentType.TEXT);
    }

    private Path write(String name, byte[] content) throws IOException {
        Path file = tempDir.resolve(name);
        Files.write(file, content);
        return file;
    }

    private byte[] zipContaining(String entryName) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write("<?xml version=\"1.0\"?><document/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }
}
