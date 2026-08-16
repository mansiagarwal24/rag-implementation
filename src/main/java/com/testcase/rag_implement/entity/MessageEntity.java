package com.testcase.rag_implement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "messages")
public class MessageEntity {

    @Id
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageRole role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "token_count")
    private Integer tokenCount;

    private String model;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // Read-only view of citations. Writes go through MessageSourceRepository so the
    // NOT NULL message_id is set on insert (avoids Hibernate's insert-then-update on
    // unidirectional @OneToMany with a join column).
    @OneToMany(fetch = FetchType.LAZY)
    @jakarta.persistence.JoinColumn(name = "message_id", insertable = false, updatable = false)
    @OrderBy("similarityScore DESC")
    private List<MessageSourceEntity> sources = new ArrayList<>();

    protected MessageEntity() {
    }

    public MessageEntity(UUID id, UUID conversationId, String tenantId, MessageRole role, String content) {
        this.id = id;
        this.conversationId = conversationId;
        this.tenantId = tenantId;
        this.role = role;
        this.content = content;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getConversationId() { return conversationId; }
    public String getTenantId() { return tenantId; }
    public MessageRole getRole() { return role; }
    public String getContent() { return content; }
    public Integer getTokenCount() { return tokenCount; }
    public String getModel() { return model; }
    public Long getLatencyMs() { return latencyMs; }
    public Instant getCreatedAt() { return createdAt; }
    public List<MessageSourceEntity> getSources() { return sources; }

    public void setTokenCount(Integer tokenCount) { this.tokenCount = tokenCount; }
    public void setModel(String model) { this.model = model; }
    public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }
}
