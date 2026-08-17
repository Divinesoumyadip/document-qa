package com.schooldesk.docqa.tenancy;

import com.schooldesk.docqa.AbstractPostgresIT;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The tenant filter has to let a browser reach the demo page and Swagger, which
 * cannot send a custom header -- but widening that whitelist is exactly how a
 * tenancy hole gets introduced later. These tests pin both halves: the browser
 * surfaces are reachable, and everything under /api still refuses a request
 * with no tenant.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TenantFilterWhitelistIT extends AbstractPostgresIT {

    @LocalServerPort
    private int port;

    @Test
    void servesTheDemoPageWithoutATenantHeader() {
        assertThat(get("/").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void servesTheOpenApiDocumentWithoutATenantHeader() {
        assertThat(get("/v3/api-docs").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void servesActuatorHealthWithoutATenantHeader() {
        assertThat(get("/actuator/health").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void stillRefusesApiRequestsWithoutATenantHeader() {
        ResponseEntity<String> response = get("/api/v1/documents");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("X-Tenant-Id");
    }

    @Test
    void stillRefusesApiRequestsWithAMalformedTenantHeader() {
        ResponseEntity<String> response = RestClient.create()
                .get()
                .uri("http://localhost:" + port + "/api/v1/documents")
                .header(TenantFilter.TENANT_HEADER, "not a valid tenant!!")
                .retrieve()
                .onStatus(s -> true, (rq, rs) -> {})
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<String> get(String path) {
        return RestClient.create()
                .get()
                .uri("http://localhost:" + port + path)
                .retrieve()
                .onStatus(s -> true, (rq, rs) -> {})
                .toEntity(String.class);
    }
}
