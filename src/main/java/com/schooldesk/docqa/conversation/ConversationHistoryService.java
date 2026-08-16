package com.schooldesk.docqa.conversation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ConversationHistoryService {

    private final JdbcTemplate jdbc;
    private final ConversationProperties properties;

    ConversationHistoryService(JdbcTemplate jdbc, ConversationProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    public List<Turn> recentTurns(UUID conversationId) {
        List<Turn> newestFirst = jdbc.query("""
                SELECT role, content FROM messages
                WHERE conversation_id = ?
                ORDER BY created_at DESC
                LIMIT ?
                """,
                (rs, rowNum) -> new Turn(rs.getString("role"), rs.getString("content")),
                conversationId, properties.maxTurns() * 2);

        List<Turn> withinBudget = applyTokenBudget(newestFirst);
        Collections.reverse(withinBudget);
        return withinBudget;
    }

    private List<Turn> applyTokenBudget(List<Turn> newestFirst) {
        List<Turn> kept = new ArrayList<>();
        int budget = properties.tokenBudget();
        int used = 0;

        for (Turn turn : newestFirst) {
            int cost = estimateTokens(turn.content());
            if (used + cost > budget) {
                break;
            }
            kept.add(turn);
            used += cost;
        }
        return kept;
    }

    public String renderForPrompt(List<Turn> turns) {
        if (turns.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("PREVIOUS CONVERSATION:\n");
        for (Turn turn : turns) {
            sb.append(turn.role().equals("user") ? "User: " : "Assistant: ")
              .append(turn.content())
              .append('\n');
        }
        return sb.append('\n').toString();
    }

    private int estimateTokens(String text) {
        return (int) Math.ceil(text.length() / 4.0);
    }

    public record Turn(String role, String content) {
    }
}
