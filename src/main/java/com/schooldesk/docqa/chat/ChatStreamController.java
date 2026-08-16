package com.schooldesk.docqa.chat;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import com.schooldesk.docqa.retrieval.RetrievalService;
import com.schooldesk.docqa.retrieval.RetrievedChunk;
import com.schooldesk.docqa.tenancy.TenantContext;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/chat/stream")
class ChatStreamController {

    private static final Logger log = LoggerFactory.getLogger(ChatStreamController.class);
    private static final long STREAM_TIMEOUT_MS = 120_000L;

    private final RetrievalService retrieval;
    private final PromptBuilder promptBuilder;
    private final StreamingChatClient streamingClient;
    private final ChatTurnWriter turnWriter;
    private final AsyncTaskExecutor executor;

    ChatStreamController(RetrievalService retrieval, PromptBuilder promptBuilder,
            StreamingChatClient streamingClient, ChatTurnWriter turnWriter,
            AsyncTaskExecutor applicationTaskExecutor) {
        this.retrieval = retrieval;
        this.promptBuilder = promptBuilder;
        this.streamingClient = streamingClient;
        this.turnWriter = turnWriter;
        this.executor = applicationTaskExecutor;
    }

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream(@Valid @RequestBody ChatRequest request) {
        String tenantId = TenantContext.require();
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);

        UUID conversationId = turnWriter.resolveConversation(request.conversationId(), tenantId);
        AtomicBoolean cancelled = new AtomicBoolean(false);

        Future<?> task = executor.submit(() -> {
            TenantContext.set(tenantId);
            try {
                runStream(request, conversationId, emitter, cancelled);
            }
            finally {
                TenantContext.clear();
            }
        });

        emitter.onCompletion(() -> cancelled.set(true));
        emitter.onTimeout(() -> {
            cancelled.set(true);
            task.cancel(true);
        });
        emitter.onError(throwable -> {
            cancelled.set(true);
            task.cancel(true);
            log.info("Client disconnected, upstream cancelled conversationId={}", conversationId);
        });

        return emitter;
    }

    private void runStream(ChatRequest request, UUID conversationId,
            SseEmitter emitter, AtomicBoolean cancelled) {
        try {
            List<RetrievedChunk> chunks = retrieval.retrieve(
                    request.question(), request.category(), null);

            if (chunks.isEmpty()) {
                emitter.send(SseEmitter.event().name("token")
                        .data(ChatService.REFUSAL_MESSAGE));
                emitter.send(SseEmitter.event().name("sources").data(List.of()));
                turnWriter.persistTurn(conversationId, request.question(),
                        ChatService.REFUSAL_MESSAGE);
                emitter.complete();
                return;
            }

            StringBuilder collected = new StringBuilder();
            String userPrompt = promptBuilder.buildUserPrompt(request.question(), chunks);

            streamingClient.stream(PromptBuilder.SYSTEM_PROMPT, userPrompt,
                    token -> {
                        if (cancelled.get()) {
                            throw new StreamCancelledException();
                        }
                        collected.append(token);
                        try {
                            emitter.send(SseEmitter.event().name("token").data(token));
                        }
                        catch (IOException disconnected) {
                            cancelled.set(true);
                            throw new StreamCancelledException();
                        }
                    },
                    () -> {
                        try {
                            emitter.send(SseEmitter.event().name("sources")
                                    .data(toSources(chunks)));
                            turnWriter.persistTurn(conversationId, request.question(),
                                    collected.toString());
                            emitter.complete();
                        }
                        catch (IOException ignored) {
                            emitter.complete();
                        }
                    },
                    emitter::completeWithError);
        }
        catch (StreamCancelledException cancelledByClient) {
            log.info("Stream cancelled by client conversationId={}", conversationId);
        }
        catch (Exception ex) {
            emitter.completeWithError(ex);
        }
    }

    private List<SourceDto> toSources(List<RetrievedChunk> chunks) {
        return chunks.stream()
                .map(c -> new SourceDto(
                        c.documentId(),
                        c.documentTitle(),
                        c.pageNumber(),
                        Math.round(c.similarity() * 10000) / 10000.0,
                        c.content().length() <= 300 ? c.content()
                                : c.content().substring(0, 300) + "..."))
                .toList();
    }

    static class StreamCancelledException extends RuntimeException {
        StreamCancelledException() {
            super(null, null, false, false);
        }
    }
}
