package com.testcase.rag_implement.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed binding for all {@code rag.*} tunables. Nothing about retrieval,
 * chunking, resilience or cost is hardcoded in business logic; it all flows from here.
 */
@ConfigurationProperties(prefix = "rag")
public record RagProperties(
        String provider,
        String refusalMessage,
        Embedding embedding,
        Chunking chunking,
        Retrieval retrieval,
        Conversation conversation,
        Upload upload,
        Llm llm,
        Cost cost
) {

    public record Embedding(int dimensions, int batchSize, String queryPrefix, String documentPrefix) {}

    public record Chunking(int maxChars, int overlapChars) {}

    public record Retrieval(int topK, double similarityThreshold) {}

    public record Conversation(int maxTurns, int maxHistoryTokens) {}

    public record Upload(long maxFileSizeBytes) {}

    public record Llm(int timeoutSeconds, Retry retry, CircuitBreaker circuitBreaker) {
        public record Retry(int maxAttempts, long initialBackoffMillis, double backoffMultiplier) {}
        public record CircuitBreaker(int failureThreshold, long openMillis) {}
    }

    public record Cost(double embeddingPer1kTokens, double chatInputPer1kTokens, double chatOutputPer1kTokens) {}
}
