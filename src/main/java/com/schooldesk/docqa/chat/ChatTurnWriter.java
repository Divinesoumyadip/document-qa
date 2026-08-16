package com.schooldesk.docqa.chat;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ChatTurnWriter {

    private final JdbcTemplate jdbc;

    ChatTurnWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public UUID resolveConversation(UUID provided, String tenantId) {
        if (provided != null) {
            Integer exists = jdbc.queryForObject(
                    "SELECT count(*) FROM conversations WHERE id = ? AND tenant_id = ?",
                    Integer.class, provided, tenantId);
            if (exists != null && exists > 0) {
                return provided;
            }
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO conversations (id, tenant_id) VALUES (?, ?)", id, tenantId);
        return id;
    }

    @Transactional
    public void persistTurn(UUID conversationId, String question, String answer) {
        jdbc.update("""
                INSERT INTO messages (id, conversation_id, role, content)
                VALUES (?, ?, 'user', ?)
                """, UUID.randomUUID(), conversationId, question);

        jdbc.update("""
                INSERT INTO messages (id, conversation_id, role, content)
                VALUES (?, ?, 'assistant', ?)
                """, UUID.randomUUID(), conversationId, answer);

        jdbc.update("UPDATE conversations SET last_message_at = now() WHERE id = ?",
                conversationId);
    }
}
