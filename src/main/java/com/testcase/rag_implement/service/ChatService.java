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

import java.util.List;

/**
 * Non-streaming chat orchestration.
 *
 * <p><b>Refusal path (FR-6):</b> if retrieval returns nothing above the similarity
 * threshold, we return a fixed refusal <em>before</em> the LLM is ever called. The model
 * is never asked to decide whether it knows the answer.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final RetrievalService retrievalService;
    private final ConversationService conversationService;
    private final LlmClient llmClient;
    private final PromptFactory promptFactory;
    private final RagMetrics metrics;
    private final String refusalMessage;

    public ChatService(RetrievalService retrievalService, ConversationService conversationService,
                       LlmClient llmClient, PromptFactory promptFactory, RagMetrics metrics, RagProperties props) {
        this.retrievalService = retrievalService;
        this.conversationService = conversationService;
        this.llmClient = llmClient;
        this.promptFactory = promptFactory;
        this.metrics = metrics;
        this.refusalMessage = props.refusalMessage();
    }

    public ChatDtos.ChatResponse chat(ChatDtos.ChatRequest request) {
        String tenantId = TenantContext.require();
        ConversationEntity conversation = conversationService.resolve(
                request.conversationId(), tenantId, request.question());

        List<RetrievedChunk> chunks = retrievalService.retrieve(tenantId, request.category(), request.question());

        // ---- REFUSAL PATH: no grounding -> do NOT call the LLM ----
        if (chunks.isEmpty()) {
            log.info("Refusal: no chunks cleared threshold; skipping LLM call. conversationId={}",
                    conversation.getId());
            conversationService.recordTurn(conversation, request.question(), refusalMessage,
                    List.of(), "none", 0, 0, 0);
            return new ChatDtos.ChatResponse(conversation.getId(), refusalMessage, false, List.of());
        }

        List<MessageEntity> history = conversationService.historyForPrompt(conversation.getId());
        String system = promptFactory.systemPrompt();
        String user = promptFactory.userPrompt(request.question(), chunks, history);

        long start = System.currentTimeMillis();
        LlmClient.LlmResult result = llmClient.complete(system, user);
        long latency = System.currentTimeMillis() - start;
        metrics.recordModelLatency(latency);
        metrics.recordChatTokens(result.inputTokens(), result.outputTokens());

        conversationService.recordTurn(conversation, request.question(), result.answer(), chunks,
                result.model(), latency, result.inputTokens(), result.outputTokens());

        return new ChatDtos.ChatResponse(conversation.getId(), result.answer(), true, toSources(chunks));
    }

    static List<ChatDtos.Source> toSources(List<RetrievedChunk> chunks) {
        return chunks.stream().map(c -> new ChatDtos.Source(
                c.documentId(), c.documentTitle(), c.pageNumber(), c.similarity(), snippet(c.content()))).toList();
    }

    private static String snippet(String content) {
        return content.length() <= 300 ? content : content.substring(0, 300) + "…";
    }
}
