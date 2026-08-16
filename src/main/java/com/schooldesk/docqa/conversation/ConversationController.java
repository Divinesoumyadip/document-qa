package com.schooldesk.docqa.conversation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.schooldesk.docqa.tenancy.TenantContext;
import com.schooldesk.docqa.web.DocumentNotFoundException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/conversations")
class ConversationController {

    private final JdbcTemplate jdbc;

    ConversationController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/{id}")
    ResponseEntity<ConversationDetail> get(@PathVariable UUID id) {
        String tenantId = TenantContext.require();

        Integer owned = jdbc.queryForObject(
                "SELECT count(*) FROM conversations WHERE id = ? AND tenant_id = ?",
                Integer.class, id, tenantId);

        if (owned == null || owned == 0) {
            throw new DocumentNotFoundException();
        }

        List<MessageDto> messages = jdbc.query("""
                SELECT role, content, created_at FROM messages
                WHERE conversation_id = ?
                ORDER BY created_at ASC
                """,
                (rs, rowNum) -> new MessageDto(
                        rs.getString("role"),
                        rs.getString("content"),
                        rs.getTimestamp("created_at").toInstant()),
                id);

        return ResponseEntity.ok(new ConversationDetail(id, messages));
    }

    record MessageDto(String role, String content, Instant createdAt) {
    }

    record ConversationDetail(UUID id, List<MessageDto> messages) {
    }
}
