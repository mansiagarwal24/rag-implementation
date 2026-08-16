package com.testcase.rag_implement.controller;

import com.testcase.rag_implement.dto.DocumentDtos;
import com.testcase.rag_implement.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Tag(name = "Documents", description = "Upload and manage source documents")
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @Operation(summary = "Upload a document (async ingestion). Returns 202 with a document id.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DocumentDtos.UploadResponse upload(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "category", required = false) String category) {
        return documentService.upload(file, title, category);
    }

    @Operation(summary = "List documents for the tenant (paginated)")
    @GetMapping
    public DocumentDtos.PagedResponse<DocumentDtos.Summary> list(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @Parameter(description = "0-based page index") @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return documentService.list(page, size);
    }

    @Operation(summary = "Get a document's details")
    @GetMapping("/{id}")
    public DocumentDtos.Detail get(@RequestHeader("X-Tenant-Id") String tenantId, @PathVariable UUID id) {
        return documentService.get(id);
    }

    @Operation(summary = "Delete a document and all its chunks/embeddings")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@RequestHeader("X-Tenant-Id") String tenantId, @PathVariable UUID id) {
        documentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
