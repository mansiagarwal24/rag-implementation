package com.testcase.rag_implement.ingestion;

import com.testcase.rag_implement.config.AsyncConfig;
import com.testcase.rag_implement.config.RagProperties;
import com.testcase.rag_implement.entity.DocumentEntity;
import com.testcase.rag_implement.llm.EmbeddingClient;
import com.testcase.rag_implement.observability.RagMetrics;
import com.testcase.rag_implement.repository.ChunkInsert;
import com.testcase.rag_implement.repository.DocumentRepository;
import com.testcase.rag_implement.util.FileTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Asynchronous ingestion pipeline: extract -> chunk -> batch-embed -> persist.
 * Runs on the bounded ingestion executor so it never blocks an HTTP thread (NFR-3).
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final List<TextExtractor> extractors;
    private final Chunker chunker;
    private final EmbeddingClient embeddingClient;
    private final IngestionPersistenceService persistence;
    private final DocumentRepository documentRepository;
    private final RagMetrics metrics;
    private final int batchSize;
    private final String documentPrefix;

    public IngestionService(List<TextExtractor> extractors, Chunker chunker, EmbeddingClient embeddingClient,
                            IngestionPersistenceService persistence, DocumentRepository documentRepository,
                            RagMetrics metrics, RagProperties props) {
        this.extractors = extractors;
        this.chunker = chunker;
        this.embeddingClient = embeddingClient;
        this.persistence = persistence;
        this.documentRepository = documentRepository;
        this.metrics = metrics;
        this.batchSize = props.embedding().batchSize();
        this.documentPrefix = props.embedding().documentPrefix() == null ? "" : props.embedding().documentPrefix();
    }

    @Async(AsyncConfig.INGESTION_EXECUTOR)
    public void ingestAsync(UUID documentId, String tenantId, String filename, String category, byte[] content) {
        long start = System.currentTimeMillis();
        try {
            String extension = FileTypes.extension(filename);
            TextExtractor extractor = extractors.stream()
                    .filter(e -> e.supports(extension))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No extractor for extension: " + extension));

            List<ExtractedPage> pages = extractor.extract(content);
            List<Chunk> chunks = chunker.chunk(pages);

            if (chunks.isEmpty()) {
                markFailed(documentId, tenantId, "No extractable text found in document");
                return;
            }

            List<ChunkInsert> inserts = embedChunks(documentId, tenantId, category, chunks);
            persistence.persistChunksAndComplete(documentId, tenantId, inserts);

            metrics.recordIngestion(System.currentTimeMillis() - start, chunks.size());
            log.info("Ingestion complete: documentId={} pages={} chunks={}", documentId, pages.size(), chunks.size());
        } catch (Exception ex) {
            log.error("Ingestion failed: documentId={} reason={}", documentId, ex.getMessage());
            markFailed(documentId, tenantId, safeReason(ex));
        }
    }

    /** Embed chunks in batches (never one call per chunk). */
    private List<ChunkInsert> embedChunks(UUID documentId, String tenantId, String category, List<Chunk> chunks) {
        List<ChunkInsert> inserts = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i += batchSize) {
            List<Chunk> batch = chunks.subList(i, Math.min(i + batchSize, chunks.size()));
            // Prefix only the text sent to the embedder; the stored content stays raw.
            List<String> texts = batch.stream().map(c -> documentPrefix + c.text()).toList();
            List<float[]> vectors = embeddingClient.embed(texts);
            for (int j = 0; j < batch.size(); j++) {
                Chunk c = batch.get(j);
                inserts.add(new ChunkInsert(UUID.randomUUID(), documentId, tenantId, category,
                        c.chunkIndex(), c.text(), c.pageNumber(), c.tokenCount(), vectors.get(j)));
            }
        }
        return inserts;
    }

    private void markFailed(UUID documentId, String tenantId, String reason) {
        documentRepository.findByIdAndTenantId(documentId, tenantId).ifPresent(doc -> {
            doc.markFailed(reason);
            documentRepository.save(doc);
        });
    }

    /** Keep provider/internal detail out of the persisted error message. */
    private String safeReason(Exception ex) {
        String type = ex.getClass().getSimpleName();
        return "Ingestion failed (" + type + ")";
    }
}
