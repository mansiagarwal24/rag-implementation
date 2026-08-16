package com.testcase.rag_implement.retrieval;

import com.testcase.rag_implement.config.RagProperties;
import com.testcase.rag_implement.llm.EmbeddingClient;
import com.testcase.rag_implement.observability.RagMetrics;
import com.testcase.rag_implement.repository.ChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Retrieval pipeline. Tenant and category filtering happen inside the SQL query (see
 * {@link ChunkRepository#search}); the similarity threshold is then applied to the small
 * top-K result set. Nothing belonging to another tenant is ever loaded into memory.
 */
@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    private final EmbeddingClient embeddingClient;
    private final ChunkRepository chunkRepository;
    private final RagMetrics metrics;
    private final int topK;
    private final double similarityThreshold;
    private final String queryPrefix;

    public RetrievalService(EmbeddingClient embeddingClient, ChunkRepository chunkRepository,
                            RagMetrics metrics, RagProperties props) {
        this.embeddingClient = embeddingClient;
        this.chunkRepository = chunkRepository;
        this.metrics = metrics;
        this.topK = props.retrieval().topK();
        this.similarityThreshold = props.retrieval().similarityThreshold();
        this.queryPrefix = props.embedding().queryPrefix() == null ? "" : props.embedding().queryPrefix();
    }

    /**
     * @param tenantId current tenant (always applied)
     * @param category optional category filter (applied in-query when non-null)
     * @param query    the user question
     * @return top-K chunks that clear the similarity threshold, ordered most-similar first
     */
    public List<RetrievedChunk> retrieve(String tenantId, String category, String query) {
        long start = System.currentTimeMillis();
        float[] queryEmbedding = embeddingClient.embedOne(queryPrefix + query);
        List<RetrievedChunk> candidates = chunkRepository.search(tenantId, category, queryEmbedding, topK);
        long elapsed = System.currentTimeMillis() - start;
        metrics.recordRetrievalLatency(elapsed);

        List<RetrievedChunk> accepted = candidates.stream()
                .filter(c -> c.similarity() >= similarityThreshold)
                .toList();

        log.info("Retrieval: category={} candidates={} accepted={} thresholdMet={} latencyMs={}",
                category, candidates.size(), accepted.size(), !accepted.isEmpty(), elapsed);
        return accepted;
    }
}
