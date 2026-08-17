package com.schooldesk.docqa.chat;

import java.util.List;
import java.util.UUID;

import com.schooldesk.docqa.retrieval.RetrievedChunk;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * All conversation writes live on this bean rather than as private methods on
 * ChatService, because @Transactional is proxy-based: a private call from
 * inside ChatService would silently not be transactional at all.
 *
 * Both the streaming and non-streaming endpoints use this, so a streamed
 * answer's citations are persisted exactly like a non-streamed one's.
 */
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

    /**
     * The turn and its citations commit together. Deliberately narrow: the
     * model call happens before this is entered, so no database connection is
     * held across the provider round trip.
     */
    @Transactional
    public void persistTurnWithSources(UUID conversationId, String question, String answer,
            List<RetrievedChunk> chunks) {

        jdbc.update("""
                INSERT INTO messages (id, conversation_id, role, content)
                VALUES (?, ?, 'user', ?)
                """, UUID.randomUUID(), conversationId, question);

        UUID assistantMessageId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO messages (id, conversation_id, role, content, token_count)
                VALUES (?, ?, 'assistant', ?, ?)
                """, assistantMessageId, conversationId, answer, estimateTokens(answer));

        for (RetrievedChunk chunk : chunks) {
            jdbc.update("""
                    INSERT INTO message_sources (id, message_id, chunk_id, similarity_score)
                    VALUES (?, ?, ?, ?)
                    """, UUID.randomUUID(), assistantMessageId, chunk.chunkId(), chunk.similarity());
        }

        jdbc.update("UPDATE conversations SET last_message_at = now() WHERE id = ?",
                conversationId);
    }

    private int estimateTokens(String text) {
        return (int) Math.ceil(text.length() / 4.0);
    }
}
