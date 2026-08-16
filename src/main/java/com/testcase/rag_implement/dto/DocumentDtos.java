package com.testcase.rag_implement.dto;

import com.testcase.rag_implement.entity.DocumentEntity;
import com.testcase.rag_implement.entity.DocumentStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Document-related response DTOs. */
public final class DocumentDtos {

    private DocumentDtos() {
    }

    public record UploadResponse(UUID documentId, DocumentStatus status) {
    }

    public record Summary(
            UUID id,
            String title,
            String category,
            DocumentStatus status,
            int chunkCount,
            long sizeBytes,
            Instant createdAt
    ) {
        public static Summary from(DocumentEntity d) {
            return new Summary(d.getId(), d.getTitle(), d.getCategory(), d.getStatus(),
                    d.getChunkCount(), d.getSizeBytes(), d.getCreatedAt());
        }
    }

    public record Detail(
            UUID id,
            String title,
            String category,
            String filename,
            DocumentStatus status,
            String errorMessage,
            int chunkCount,
            long sizeBytes,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static Detail from(DocumentEntity d) {
            return new Detail(d.getId(), d.getTitle(), d.getCategory(), d.getFilename(), d.getStatus(),
                    d.getErrorMessage(), d.getChunkCount(), d.getSizeBytes(), d.getCreatedAt(), d.getUpdatedAt());
        }
    }

    public record PagedResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    }
}
