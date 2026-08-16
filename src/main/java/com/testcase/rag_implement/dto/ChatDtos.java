package com.testcase.rag_implement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Chat + conversation DTOs. */
public final class ChatDtos {

    private ChatDtos() {
    }

    public record ChatRequest(
            UUID conversationId,
            @NotBlank(message = "question must not be blank")
            @Size(max = 4000, message = "question must be at most 4000 characters")
            String question,
            String category
    ) {
    }

    public record Source(
            UUID documentId,
            String documentTitle,
            Integer pageNumber,
            double similarityScore,
            String snippet
    ) {
    }

    public record ChatResponse(
            UUID conversationId,
            String answer,
            boolean grounded,
            List<Source> sources
    ) {
    }

    public record MessageView(String role, String content, Instant createdAt, List<Source> sources) {
    }

    public record ConversationResponse(UUID id, String title, Instant createdAt, List<MessageView> messages) {
    }
}
