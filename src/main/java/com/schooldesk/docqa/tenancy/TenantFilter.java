package com.schooldesk.docqa.tenancy;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TenantFilter extends OncePerRequestFilter {

    public static final String TENANT_HEADER = "X-Tenant-Id";

    private static final Pattern VALID_TENANT = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    /**
     * Surfaces a browser reaches directly, which cannot attach a custom header:
     * the demo page, Swagger UI, the OpenAPI document, and infrastructure
     * endpoints. Everything under /api stays tenant-scoped -- widening this
     * list is how a tenancy hole gets introduced, so it is deliberately short
     * and has a test asserting /api/** still rejects a missing header.
     */
    private static final List<String> UNSCOPED_PREFIXES = List.of(
            "/actuator", "/swagger-ui", "/v3/api-docs", "/webjars");

    private static final List<String> UNSCOPED_EXACT = List.of(
            "/", "/index.html", "/favicon.ico");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        String tenantId = request.getHeader(TENANT_HEADER);

        if (tenantId == null || !VALID_TENANT.matcher(tenantId).matches()) {
            writeMissingTenantProblem(response);
            return;
        }

        try {
            TenantContext.set(tenantId);
            chain.doFilter(request, response);
        }
        finally {
            TenantContext.clear();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (UNSCOPED_EXACT.contains(uri)) {
            return true;
        }
        return UNSCOPED_PREFIXES.stream().anyMatch(uri::startsWith);
    }

    private void writeMissingTenantProblem(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("""
                {"type":"about:blank","title":"Bad Request","status":400,\
                "detail":"A valid X-Tenant-Id header is required."}""");
    }
}
