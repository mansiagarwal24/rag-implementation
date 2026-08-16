package com.testcase.rag_implement.controller;

import com.testcase.rag_implement.dto.ChatDtos;
import com.testcase.rag_implement.service.ChatService;
import com.testcase.rag_implement.service.ChatStreamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Chat", description = "Ask grounded questions with citations")
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;
    private final ChatStreamService chatStreamService;

    public ChatController(ChatService chatService, ChatStreamService chatStreamService) {
        this.chatService = chatService;
        this.chatStreamService = chatStreamService;
    }

    @Operation(summary = "Ask a question (non-streaming). Refuses when nothing clears the threshold.")
    @PostMapping
    public ChatDtos.ChatResponse chat(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @Valid @RequestBody ChatDtos.ChatRequest request) {
        return chatService.chat(request);
    }

    @Operation(summary = "Ask a question (streaming SSE). Sources arrive as a terminal event.")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @Valid @RequestBody ChatDtos.ChatRequest request) {
        return chatStreamService.stream(request);
    }
}
