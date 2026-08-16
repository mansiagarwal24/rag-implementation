package com.testcase.rag_implement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "message_sources")
public class MessageSourceEntity {

    @Id
    private UUID id;

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "chunk_id")
    private UUID chunkId;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "document_title")
    private String documentTitle;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(name = "similarity_score")
    private Double similarityScore;

    @Column(name = "snippet", columnDefinition = "text")
    private String snippet;

    protected MessageSourceEntity() {
    }

    public MessageSourceEntity(UUID id, UUID messageId, UUID chunkId, UUID documentId, String documentTitle,
                               Integer pageNumber, Double similarityScore, String snippet) {
        this.id = id;
        this.messageId = messageId;
        this.chunkId = chunkId;
        this.documentId = documentId;
        this.documentTitle = documentTitle;
        this.pageNumber = pageNumber;
        this.similarityScore = similarityScore;
        this.snippet = snippet;
    }

    public UUID getId() { return id; }
    public UUID getMessageId() { return messageId; }
    public UUID getChunkId() { return chunkId; }
    public UUID getDocumentId() { return documentId; }
    public String getDocumentTitle() { return documentTitle; }
    public Integer getPageNumber() { return pageNumber; }
    public Double getSimilarityScore() { return similarityScore; }
    public String getSnippet() { return snippet; }
}
