package com.schooldesk.docqa.chat;

import java.util.List;
import java.util.UUID;

import com.schooldesk.docqa.conversation.ConversationHistoryService;
import com.schooldesk.docqa.retrieval.RetrievalService;
import com.schooldesk.docqa.retrieval.RetrievedChunk;
import com.schooldesk.docqa.tenancy.TenantContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    public static final String REFUSAL_MESSAGE =
            "I could not find that in the available documents.";

    private final RetrievalService retrieval;
    private final PromptBuilder promptBuilder;
    private final ChatClient chatClient;
    private final ConversationHistoryService history;
    private final JdbcTemplate jdbc;

    ChatService(RetrievalService retrieval, PromptBuilder promptBuilder,
            ChatClient chatClient, ConversationHistoryService history, JdbcTemplate jdbc) {
        this.retrieval = retrieval;
        this.promptBuilder = promptBuilder;
        this.chatClient = chatClient;
        this.history = history;
        this.jdbc = jdbc;
    }

    @Transactional
    public ChatResponse ask(ChatRequest request) {
        String tenantId = TenantContext.require();
        UUID conversationId = resolveConversation(request.conversationId(), tenantId);

        List<RetrievedChunk> chunks = retrieval.retrieve(
                request.question(), request.category(), null);

        if (chunks.isEmpty()) {
            log.info("Refusal path fired tenantId={} conversationId={}", tenantId, conversationId);
            persistTurn(conversationId, request.question(), REFUSAL_MESSAGE);
            return new ChatResponse(conversationId, REFUSAL_MESSAGE, false, List.of());
        }

        // Prior turns are prepended so a follow-up like "what about class 9?"
        // resolves against what was actually asked before, not in isolation.
        List<ConversationHistoryService.Turn> priorTurns = history.recentTurns(conversationId);
        String historyBlock = history.renderForPrompt(priorTurns);
        String userPrompt = historyBlock + promptBuilder.buildUserPrompt(request.question(), chunks);

        String answer = chatClient.complete(PromptBuilder.SYSTEM_PROMPT, userPrompt);

        UUID assistantMessageId = persistTurn(conversationId, request.question(), answer);
        persistSources(assistantMessageId, chunks);

        List<SourceDto> sources = chunks.stream()
                .map(c -> new SourceDto(
                        c.documentId(),
                        c.documentTitle(),
                        c.pageNumber(),
                        Math.round(c.similarity() * 10000) / 10000.0,
                        truncate(c.content())))
                .toList();

        return new ChatResponse(conversationId, answer, true, sources);
    }

    private UUID resolveConversation(UUID provided, String tenantId) {
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

    /** Returns the assistant message id so callers can attach sources to it. */
    private UUID persistTurn(UUID conversationId, String question, String answer) {
        jdbc.update("""
                INSERT INTO messages (id, conversation_id, role, content)
                VALUES (?, ?, 'user', ?)
                """, UUID.randomUUID(), conversationId, question);

        UUID assistantMessageId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO messages (id, conversation_id, role, content)
                VALUES (?, ?, 'assistant', ?)
                """, assistantMessageId, conversationId, answer);

        jdbc.update("UPDATE conversations SET last_message_at = now() WHERE id = ?",
                conversationId);

        return assistantMessageId;
    }

    private void persistSources(UUID messageId, List<RetrievedChunk> chunks) {
        for (RetrievedChunk chunk : chunks) {
            jdbc.update("""
                    INSERT INTO message_sources (id, message_id, chunk_id, similarity_score)
                    VALUES (?, ?, ?, ?)
                    """, UUID.randomUUID(), messageId, chunk.chunkId(), chunk.similarity());
        }
    }

    private String truncate(String content) {
        return content.length() <= 300 ? content : content.substring(0, 300) + "...";
    }
}
