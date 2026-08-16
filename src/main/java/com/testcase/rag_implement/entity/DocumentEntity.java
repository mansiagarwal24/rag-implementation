package com.testcase.rag_implement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "documents")
public class DocumentEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    private String title;

    private String category;

    @Column(nullable = false)
    private String filename;

    @Column(name = "content_hash", nullable = false)
    private String contentHash;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DocumentEntity() {
    }

    public DocumentEntity(UUID id, String tenantId, String title, String category, String filename,
                          String contentHash, long sizeBytes, DocumentStatus status) {
        this.id = id;
        this.tenantId = tenantId;
        this.title = title;
        this.category = category;
        this.filename = filename;
        this.contentHash = contentHash;
        this.sizeBytes = sizeBytes;
        this.status = status;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void markReady(int chunkCount) {
        this.status = DocumentStatus.READY;
        this.chunkCount = chunkCount;
        this.errorMessage = null;
    }

    public void markFailed(String reason) {
        this.status = DocumentStatus.FAILED;
        this.errorMessage = reason;
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getTitle() { return title; }
    public String getCategory() { return category; }
    public String getFilename() { return filename; }
    public String getContentHash() { return contentHash; }
    public long getSizeBytes() { return sizeBytes; }
    public DocumentStatus getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
    public int getChunkCount() { return chunkCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
