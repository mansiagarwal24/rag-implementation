package com.testcase.rag_implement.retrieval;

import java.util.UUID;

/** A chunk returned from vector search, with its cosine similarity score (0..1, higher is closer). */
public record RetrievedChunk(
        UUID chunkId,
        UUID documentId,
        String documentTitle,
        String content,
        Integer pageNumber,
        String category,
        double similarity
) {
}
