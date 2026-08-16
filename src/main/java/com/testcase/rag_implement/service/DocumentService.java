package com.testcase.rag_implement.service;

import com.testcase.rag_implement.config.RagProperties;
import com.testcase.rag_implement.dto.DocumentDtos;
import com.testcase.rag_implement.entity.DocumentEntity;
import com.testcase.rag_implement.entity.DocumentStatus;
import com.testcase.rag_implement.exception.ApiExceptions;
import com.testcase.rag_implement.ingestion.IngestionService;
import com.testcase.rag_implement.repository.DocumentRepository;
import com.testcase.rag_implement.tenant.TenantContext;
import com.testcase.rag_implement.util.FileTypes;
import com.testcase.rag_implement.util.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository documentRepository;
    private final IngestionService ingestionService;
    private final long maxFileSizeBytes;

    public DocumentService(DocumentRepository documentRepository, IngestionService ingestionService,
                           RagProperties props) {
        this.documentRepository = documentRepository;
        this.ingestionService = ingestionService;
        this.maxFileSizeBytes = props.upload().maxFileSizeBytes();
    }

    /**
     * Validate + persist a PROCESSING document and kick off async ingestion.
     * Idempotent on (tenant, SHA-256): re-uploading the same content returns the
     * existing document instead of creating duplicate chunks.
     */
    public DocumentDtos.UploadResponse upload(MultipartFile file, String title, String category) {
        String tenantId = TenantContext.require();

        if (file == null || file.isEmpty()) {
            throw new ApiExceptions.BadRequestException("File is required");
        }
        String filename = file.getOriginalFilename();
        if (!FileTypes.isSupported(filename)) {
            throw new ApiExceptions.UnsupportedFileTypeException(
                    "Unsupported file type. Supported: PDF, DOCX, TXT, Markdown");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new ApiExceptions.PayloadTooLargeException("File exceeds maximum size");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new ApiExceptions.BadRequestException("Could not read uploaded file");
        }
        // Enforce again on actual bytes (multipart size can be unreliable).
        if (content.length > maxFileSizeBytes) {
            throw new ApiExceptions.PayloadTooLargeException("File exceeds maximum size");
        }

        String hash = HashUtil.sha256Hex(content);

        // Fast idempotency path.
        var existing = documentRepository.findByTenantIdAndContentHash(tenantId, hash);
        if (existing.isPresent()) {
            log.info("Idempotent upload: existing documentId={}", existing.get().getId());
            return new DocumentDtos.UploadResponse(existing.get().getId(), existing.get().getStatus());
        }

        DocumentEntity document = new DocumentEntity(UUID.randomUUID(), tenantId,
                title != null ? title : filename, normalizeCategory(category), filename, hash,
                content.length, DocumentStatus.PROCESSING);

        try {
            documentRepository.saveAndFlush(document);
        } catch (DataIntegrityViolationException race) {
            // Concurrent duplicate upload: the unique constraint won. Return the winner.
            return documentRepository.findByTenantIdAndContentHash(tenantId, hash)
                    .map(d -> new DocumentDtos.UploadResponse(d.getId(), d.getStatus()))
                    .orElseThrow(() -> race);
        }

        ingestionService.ingestAsync(document.getId(), tenantId, filename, document.getCategory(), content);
        return new DocumentDtos.UploadResponse(document.getId(), DocumentStatus.PROCESSING);
    }

    @Transactional(readOnly = true)
    public DocumentDtos.PagedResponse<DocumentDtos.Summary> list(int page, int size) {
        String tenantId = TenantContext.require();
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        Page<DocumentEntity> result = documentRepository.findByTenantId(tenantId, pageable);
        return new DocumentDtos.PagedResponse<>(
                result.getContent().stream().map(DocumentDtos.Summary::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public DocumentDtos.Detail get(UUID id) {
        String tenantId = TenantContext.require();
        return documentRepository.findByIdAndTenantId(id, tenantId)
                .map(DocumentDtos.Detail::from)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Document not found"));
    }

    /** Physically deletes the document; chunks/embeddings cascade. Deleted content stops being retrievable at once. */
    @Transactional
    public void delete(UUID id) {
        String tenantId = TenantContext.require();
        DocumentEntity document = documentRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Document not found"));
        documentRepository.delete(document);
        log.info("Deleted documentId={}", id);
    }

    private String normalizeCategory(String category) {
        return (category == null || category.isBlank()) ? null : category.trim().toUpperCase();
    }
}
