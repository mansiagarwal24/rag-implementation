package com.testcase.rag_implement.service;

import com.testcase.rag_implement.config.RagProperties;
import com.testcase.rag_implement.dto.ChatDtos;
import com.testcase.rag_implement.entity.ConversationEntity;
import com.testcase.rag_implement.entity.MessageEntity;
import com.testcase.rag_implement.entity.MessageRole;
import com.testcase.rag_implement.entity.MessageSourceEntity;
import com.testcase.rag_implement.exception.ApiExceptions;
import com.testcase.rag_implement.ingestion.TokenEstimator;
import com.testcase.rag_implement.repository.ConversationRepository;
import com.testcase.rag_implement.repository.MessageRepository;
import com.testcase.rag_implement.repository.MessageSourceRepository;
import com.testcase.rag_implement.retrieval.RetrievedChunk;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * Owns conversation persistence and history windowing. History is capped by BOTH a turn
 * count and a token budget: we walk backwards from the newest message and stop when either
 * limit is hit, so a few very long turns cannot blow the prompt size.
 */
@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final MessageSourceRepository messageSourceRepository;
    private final int maxTurns;
    private final int maxHistoryTokens;

    public ConversationService(ConversationRepository conversationRepository, MessageRepository messageRepository,
                               MessageSourceRepository messageSourceRepository, RagProperties props) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.messageSourceRepository = messageSourceRepository;
        this.maxTurns = props.conversation().maxTurns();
        this.maxHistoryTokens = props.conversation().maxHistoryTokens();
    }

    /** Resolve an existing tenant-owned conversation or create a new one. */
    @Transactional
    public ConversationEntity resolve(UUID conversationId, String tenantId, String seedTitle) {
        if (conversationId == null) {
            ConversationEntity created = new ConversationEntity(UUID.randomUUID(), tenantId, truncateTitle(seedTitle));
            return conversationRepository.save(created);
        }
        return conversationRepository.findByIdAndTenantId(conversationId, tenantId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Conversation not found"));
    }

    /** Recent history, bounded by turn count and token budget, in chronological order. */
    @Transactional(readOnly = true)
    public List<MessageEntity> historyForPrompt(UUID conversationId) {
        List<MessageEntity> all = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        Deque<MessageEntity> selected = new ArrayDeque<>();
        int tokens = 0;
        int turns = 0;
        for (int i = all.size() - 1; i >= 0; i--) {
            MessageEntity m = all.get(i);
            int cost = m.getTokenCount() != null ? m.getTokenCount() : TokenEstimator.estimate(m.getContent());
            if (!selected.isEmpty() && (tokens + cost > maxHistoryTokens || turns >= maxTurns * 2)) {
                break;
            }
            selected.addFirst(m);
            tokens += cost;
            turns++;
        }
        return new ArrayList<>(selected);
    }

    /** Persist the user question and the assistant answer (with citations) for a turn. */
    @Transactional
    public MessageEntity recordTurn(ConversationEntity conversation, String question, String answer,
                                    List<RetrievedChunk> sources, String model, long latencyMs,
                                    int inputTokens, int outputTokens) {
        MessageEntity userMessage = new MessageEntity(UUID.randomUUID(), conversation.getId(),
                conversation.getTenantId(), MessageRole.USER, question);
        userMessage.setTokenCount(TokenEstimator.estimate(question));
        messageRepository.save(userMessage);

        MessageEntity assistantMessage = new MessageEntity(UUID.randomUUID(), conversation.getId(),
                conversation.getTenantId(), MessageRole.ASSISTANT, answer);
        assistantMessage.setModel(model);
        assistantMessage.setLatencyMs(latencyMs);
        assistantMessage.setTokenCount(outputTokens > 0 ? outputTokens : TokenEstimator.estimate(answer));
        messageRepository.save(assistantMessage);

        for (RetrievedChunk c : sources) {
            messageSourceRepository.save(new MessageSourceEntity(UUID.randomUUID(), assistantMessage.getId(),
                    c.chunkId(), c.documentId(), c.documentTitle(), c.pageNumber(), c.similarity(),
                    snippet(c.content())));
        }

        conversation.touch();
        conversationRepository.save(conversation);
        return assistantMessage;
    }

    @Transactional(readOnly = true)
    public ChatDtos.ConversationResponse getConversation(UUID id, String tenantId) {
        ConversationEntity conversation = conversationRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Conversation not found"));
        List<MessageEntity> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(id);
        List<ChatDtos.MessageView> views = messages.stream().map(m -> new ChatDtos.MessageView(
                m.getRole().name(), m.getContent(), m.getCreatedAt(),
                m.getSources().stream().map(s -> new ChatDtos.Source(
                        s.getDocumentId(), s.getDocumentTitle(), s.getPageNumber(),
                        s.getSimilarityScore() == null ? 0.0 : s.getSimilarityScore(), s.getSnippet())).toList()
        )).toList();
        return new ChatDtos.ConversationResponse(conversation.getId(), conversation.getTitle(),
                conversation.getCreatedAt(), views);
    }

    private String snippet(String content) {
        return content.length() <= 300 ? content : content.substring(0, 300) + "…";
    }

    private String truncateTitle(String title) {
        if (title == null) {
            return "New conversation";
        }
        return title.length() <= 120 ? title : title.substring(0, 120);
    }
}
