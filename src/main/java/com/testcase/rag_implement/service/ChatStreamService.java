package com.testcase.rag_implement.service;

import com.testcase.rag_implement.config.RagProperties;
import com.testcase.rag_implement.dto.ChatDtos;
import com.testcase.rag_implement.entity.ConversationEntity;
import com.testcase.rag_implement.entity.MessageEntity;
import com.testcase.rag_implement.llm.LlmClient;
import com.testcase.rag_implement.llm.PromptFactory;
import com.testcase.rag_implement.observability.RagMetrics;
import com.testcase.rag_implement.retrieval.RetrievalService;
import com.testcase.rag_implement.retrieval.RetrievedChunk;
import com.testcase.rag_implement.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Streaming chat over SSE. Tokens stream as they arrive; sources are emitted as a distinct
 * terminal event. If the client disconnects, the emitter callbacks dispose the reactor
 * subscription, which cancels the upstream provider request — no orphaned model calls.
 */
@Service
public class ChatStreamService {

    private static final Logger log = LoggerFactory.getLogger(ChatStreamService.class);

    private final RetrievalService retrievalService;
    private final ConversationService conversationService;
    private final LlmClient llmClient;
    private final PromptFactory promptFactory;
    private final RagMetrics metrics;
    private final String refusalMessage;

    public ChatStreamService(RetrievalService retrievalService, ConversationService conversationService,
                             LlmClient llmClient, PromptFactory promptFactory, RagMetrics metrics,
                             RagProperties props) {
        this.retrievalService = retrievalService;
        this.conversationService = conversationService;
        this.llmClient = llmClient;
        this.promptFactory = promptFactory;
        this.metrics = metrics;
        this.refusalMessage = props.refusalMessage();
    }

    public SseEmitter stream(ChatDtos.ChatRequest request) {
        String tenantId = TenantContext.require();
        SseEmitter emitter = new SseEmitter(120_000L);

        ConversationEntity conversation = conversationService.resolve(
                request.conversationId(), tenantId, request.question());
        List<RetrievedChunk> chunks = retrievalService.retrieve(tenantId, request.category(), request.question());

        // ---- REFUSAL PATH: no LLM call ----
        if (chunks.isEmpty()) {
            try {
                emitter.send(SseEmitter.event().name("token").data(refusalMessage));
                emitter.send(SseEmitter.event().name("sources").data(List.of()));
                emitter.send(SseEmitter.event().name("complete").data("{}"));
                conversationService.recordTurn(conversation, request.question(), refusalMessage,
                        List.of(), "none", 0, 0, 0);
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }

        List<MessageEntity> history = conversationService.historyForPrompt(conversation.getId());
        String system = promptFactory.systemPrompt();
        String user = promptFactory.userPrompt(request.question(), chunks, history);

        StringBuilder answer = new StringBuilder();
        long start = System.currentTimeMillis();
        AtomicLong firstTokenAt = new AtomicLong(0);

        Disposable subscription = llmClient.stream(system, user)
                .subscribe(
                        token -> {
                            try {
                                if (firstTokenAt.get() == 0) {
                                    firstTokenAt.set(System.currentTimeMillis());
                                }
                                answer.append(token);
                                emitter.send(SseEmitter.event().name("token").data(token));
                            } catch (IOException e) {
                                // Client is gone; abort so the upstream subscription is disposed.
                                throw new RuntimeException(e);
                            }
                        },
                        error -> {
                            log.warn("Stream error: {}", error.getMessage());
                            emitter.completeWithError(error);
                        },
                        () -> {
                            try {
                                long latency = System.currentTimeMillis() - start;
                                metrics.recordModelLatency(latency);
                                emitter.send(SseEmitter.event().name("sources")
                                        .data(ChatService.toSources(chunks)));
                                emitter.send(SseEmitter.event().name("complete").data("{}"));
                                conversationService.recordTurn(conversation, request.question(),
                                        answer.toString(), chunks, llmClient.model(), latency, 0, 0);
                                emitter.complete();
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        });

        // On client disconnect/timeout/error, dispose the subscription to cancel upstream.
        emitter.onCompletion(subscription::dispose);
        emitter.onTimeout(() -> {
            subscription.dispose();
            emitter.complete();
        });
        emitter.onError(e -> subscription.dispose());
        return emitter;
    }
}
