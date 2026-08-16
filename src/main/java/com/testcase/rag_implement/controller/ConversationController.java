package com.testcase.rag_implement.controller;

import com.testcase.rag_implement.dto.ChatDtos;
import com.testcase.rag_implement.service.ConversationService;
import com.testcase.rag_implement.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Conversations", description = "Retrieve conversation history")
@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @Operation(summary = "Get full conversation history (tenant-scoped)")
    @GetMapping("/{id}")
    public ChatDtos.ConversationResponse get(@RequestHeader("X-Tenant-Id") String tenantId, @PathVariable UUID id) {
        return conversationService.getConversation(id, TenantContext.require());
    }
}
