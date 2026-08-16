package com.schooldesk.docqa.documents;

import java.nio.charset.StandardCharsets;

import com.schooldesk.docqa.AbstractPostgresIT;
import com.schooldesk.docqa.tenancy.TenantFilter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.servlet.multipart.max-file-size=1MB",
        "spring.servlet.multipart.max-request-size=2MB"
})
class UploadSizeLimitIT extends AbstractPostgresIT {

    @LocalServerPort
    private int port;

    @Test
    void refusesAFileLargerThanTheConfiguredLimitWithPayloadTooLarge() {
        byte[] oversized = new byte[2 * 1024 * 1024];
        System.arraycopy("%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII), 0, oversized, 0, 9);

        ResponseEntity<String> response = post(oversized, "huge.pdf");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(response.getBody()).contains("maximum upload size");
        assertThat(response.getBody()).doesNotContain("Exception");
    }

    @Test
    void acceptsAFileUnderTheConfiguredLimit() {
        byte[] small = "%PDF-1.7\n1 0 obj\n<< /Type /Catalog >>\nendobj\n"
                .getBytes(StandardCharsets.US_ASCII);

        assertThat(post(small, "small-limit.pdf").getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);
    }

    private ResponseEntity<String> post(byte[] content, String filename) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(content) {
            @Override public String getFilename() { return filename; }
        });

        return RestClient.create()
                .post()
                .uri("http://localhost:" + port + "/api/v1/documents")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .header(TenantFilter.TENANT_HEADER, "tenant-size")
                .body(form)
                .retrieve()
                .onStatus(s -> true, (rq, rs) -> {})
                .toEntity(String.class);
    }
}
