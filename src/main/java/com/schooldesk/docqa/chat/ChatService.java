package com.schooldesk.docqa.chat;

import java.util.List;
import java.util.UUID;

import com.schooldesk.docqa.conversation.ConversationHistoryService;
import com.schooldesk.docqa.retrieval.RetrievalService;
import com.schooldesk.docqa.retrieval.RetrievedChunk;
import com.schooldesk.docqa.tenancy.TenantContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    public static final String REFUSAL_MESSAGE =
            "I could not find that in the available documents.";

    private final RetrievalService retrieval;
    private final PromptBuilder promptBuilder;
    private final ChatClient chatClient;
    private final ConversationHistoryService history;
    private final ChatTurnWriter turnWriter;

    ChatService(RetrievalService retrieval, PromptBuilder promptBuilder, ChatClient chatClient,
            ConversationHistoryService history, ChatTurnWriter turnWriter) {
        this.retrieval = retrieval;
        this.promptBuilder = promptBuilder;
        this.chatClient = chatClient;
        this.history = history;
        this.turnWriter = turnWriter;
    }

    /**
     * Deliberately not @Transactional. The model call can take seconds and
     * retries with backoff; holding one of ten pooled connections across it
     * would make the database the bottleneck long before the provider is.
     * Only the write at the end is transactional, inside ChatTurnWriter.
     */
    public ChatResponse ask(ChatRequest request) {
        String tenantId = TenantContext.require();
        UUID conversationId = turnWriter.resolveConversation(request.conversationId(), tenantId);

        List<RetrievedChunk> chunks = retrieval.retrieve(
                request.question(), request.category(), null);

        if (chunks.isEmpty()) {
            log.info("Refusal path fired tenantId={} conversationId={}", tenantId, conversationId);
            turnWriter.persistTurnWithSources(
                    conversationId, request.question(), REFUSAL_MESSAGE, List.of());
            return new ChatResponse(conversationId, REFUSAL_MESSAGE, false, List.of());
        }

        String historyBlock = history.renderForPrompt(history.recentTurns(conversationId));
        String userPrompt = historyBlock + promptBuilder.buildUserPrompt(request.question(), chunks);

        String answer = chatClient.complete(PromptBuilder.SYSTEM_PROMPT, userPrompt);

        turnWriter.persistTurnWithSources(conversationId, request.question(), answer, chunks);

        return new ChatResponse(conversationId, answer, true, SourceDto.from(chunks));
    }
}
