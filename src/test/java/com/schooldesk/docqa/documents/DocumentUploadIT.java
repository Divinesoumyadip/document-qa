package com.schooldesk.docqa.documents;

import java.nio.charset.StandardCharsets;

import com.schooldesk.docqa.AbstractPostgresIT;
import com.schooldesk.docqa.tenancy.TenantFilter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DocumentUploadIT extends AbstractPostgresIT {

    private static final byte[] PDF_BYTES =
            "%PDF-1.7\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n"
                    .getBytes(StandardCharsets.US_ASCII);

    @LocalServerPort
    private int port;

    @Autowired
    private DocumentRepository documents;

    @Test
    void acceptsAPdfAndReportsItAsProcessing() {
        ResponseEntity<String> res = post("tenant-upload-a", "fee-policy.pdf", "Fee Policy 2024", PDF_BYTES);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(res.getBody()).contains("PROCESSING");
        assertThat(res.getBody()).contains("Fee Policy 2024");
    }

    @Test
    void rejectsAnExecutableRenamedToPdfWithUnsupportedMediaType() {
        byte[] executable = { 'M', 'Z', (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00 };

        ResponseEntity<String> res = post("tenant-upload-b", "admissions.pdf", null, executable);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(res.getBody()).contains("Unsupported Media Type");
    }

    @Test
    void rejectsAnEmptyFileWithBadRequest() {
        ResponseEntity<String> res = post("tenant-upload-c", "empty.pdf", null, new byte[0]);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsARequestWithNoTenantHeaderWithBadRequest() {
        ResponseEntity<String> res = postNoTenant("fee-policy.pdf", PDF_BYTES);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody()).contains("X-Tenant-Id");
    }

    @Test
    void returnsTheExistingDocumentWhenTheSameFileIsUploadedTwice() {
        ResponseEntity<String> first = post("tenant-idem", "idempotent.pdf", "Idem", PDF_BYTES);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        ResponseEntity<String> second = post("tenant-idem", "idempotent.pdf", "Idem", PDF_BYTES);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(documents.findByTenantIdOrderByCreatedAtDesc(
                "tenant-idem", PageRequest.of(0, 50)).getContent()).hasSize(1);
    }

    @Test
    void treatsTheSameFileUploadedByADifferentTenantAsADistinctDocument() {
        post("tenant-cross-a", "shared-cross.pdf", "Shared", PDF_BYTES);

        ResponseEntity<String> res = post("tenant-cross-b", "shared-cross.pdf", "Shared", PDF_BYTES);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    private ResponseEntity<String> post(String tenantId, String filename, String title, byte[] content) {
        MultiValueMap<String, Object> form = buildForm(filename, title, content);
        return RestClient.create()
                .post()
                .uri("http://localhost:" + port + "/api/v1/documents")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .header(TenantFilter.TENANT_HEADER, tenantId)
                .body(form)
                .retrieve()
                .onStatus(s -> true, (rq, rs) -> {})
                .toEntity(String.class);
    }

    private ResponseEntity<String> postNoTenant(String filename, byte[] content) {
        MultiValueMap<String, Object> form = buildForm(filename, null, content);
        return RestClient.create()
                .post()
                .uri("http://localhost:" + port + "/api/v1/documents")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .onStatus(s -> true, (rq, rs) -> {})
                .toEntity(String.class);
    }

    private MultiValueMap<String, Object> buildForm(String filename, String title, byte[] content) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(content) {
            @Override public String getFilename() { return filename; }
        });
        if (title != null) {
            form.add("title", title);
        }
        return form;
    }
}
