package com.testcase.rag_implement.repository;

import java.util.UUID;

/** Value carried from the ingestion pipeline into a batched chunk insert. */
public record ChunkInsert(
        UUID id,
        UUID documentId,
        String tenantId,
        String category,
        int chunkIndex,
        String content,
        Integer pageNumber,
        int tokenCount,
        float[] embedding
) {
}
