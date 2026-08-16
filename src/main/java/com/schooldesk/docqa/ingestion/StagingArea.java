package com.schooldesk.docqa.ingestion;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class StagingArea {

    private static final Logger log = LoggerFactory.getLogger(StagingArea.class);

    private final IngestionProperties properties;

    StagingArea(IngestionProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void createDirectory() throws IOException {
        Files.createDirectories(properties.stagingDirectory());
    }

    public StagedFile stage(MultipartFile upload) throws IOException {
        Path target = properties.stagingDirectory().resolve(UUID.randomUUID() + ".staged");
        MessageDigest digest = sha256();

        try (InputStream in = new DigestInputStream(upload.getInputStream(), digest);
                OutputStream out = Files.newOutputStream(target)) {
            in.transferTo(out);
        }

        return new StagedFile(target, HexFormat.of().formatHex(digest.digest()), Files.size(target));
    }

    public void discard(Path staged) {
        try {
            Files.deleteIfExists(staged);
        }
        catch (IOException ex) {

            log.warn("Could not delete staged file {}", staged.getFileName(), ex);
        }
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        }
        catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the JDK spec", impossible);
        }
    }

    public record StagedFile(Path path, String sha256, long sizeBytes) {
    }
}
