package com.testcase.rag_implement.ingestion;

import com.testcase.rag_implement.entity.DocumentEntity;
import com.testcase.rag_implement.exception.ApiExceptions;
import com.testcase.rag_implement.repository.ChunkInsert;
import com.testcase.rag_implement.repository.ChunkRepository;
import com.testcase.rag_implement.repository.DocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Holds the single short transaction per document: insert all chunks + embeddings and
 * flip the document to READY atomically. If anything here fails, the whole thing rolls
 * back and the document is left for the caller to mark FAILED — never a half-ingested READY.
 *
 * <p>The slow external embedding work happens <em>before</em> this method, so no DB
 * transaction is held open while waiting on the provider.
 */
@Service
public class IngestionPersistenceService {

    private final ChunkRepository chunkRepository;
    private final DocumentRepository documentRepository;

    public IngestionPersistenceService(ChunkRepository chunkRepository, DocumentRepository documentRepository) {
        this.chunkRepository = chunkRepository;
        this.documentRepository = documentRepository;
    }

    @Transactional
    public void persistChunksAndComplete(UUID documentId, String tenantId, List<ChunkInsert> chunks) {
        // Re-check inside the transaction: if the document was deleted mid-ingestion,
        // abort rather than resurrecting deleted content.
        DocumentEntity document = documentRepository.findByIdAndTenantId(documentId, tenantId)
                .orElseThrow(() -> new ApiExceptions.NotFoundException("Document deleted during ingestion"));

        chunkRepository.insertBatch(chunks);
        document.markReady(chunks.size());
        documentRepository.save(document);
    }
}
