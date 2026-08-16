package com.testcase.rag_implement.observability;

import com.testcase.rag_implement.config.RagProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Per-request RAG metrics: retrieval latency, model latency, token counts and estimated cost.
 * Cost is derived from configurable per-1K-token prices so it is provider-independent.
 */
@Component
public class RagMetrics {

    private static final Logger log = LoggerFactory.getLogger(RagMetrics.class);

    private final MeterRegistry registry;
    private final RagProperties.Cost cost;

    public RagMetrics(MeterRegistry registry, RagProperties props) {
        this.registry = registry;
        this.cost = props.cost();
    }

    public void recordRetrievalLatency(long millis) {
        registry.timer("rag.retrieval.latency").record(millis, TimeUnit.MILLISECONDS);
    }

    public void recordModelLatency(long millis) {
        registry.timer("rag.model.latency").record(millis, TimeUnit.MILLISECONDS);
    }

    public void recordChatTokens(int inputTokens, int outputTokens) {
        registry.counter("rag.tokens.input").increment(inputTokens);
        registry.counter("rag.tokens.output").increment(outputTokens);
        double estimated = estimateChatCost(inputTokens, outputTokens);
        registry.counter("rag.cost.usd").increment(estimated);
        log.info("Chat tokens in={} out={} estCostUsd={}", inputTokens, outputTokens, String.format("%.6f", estimated));
    }

    public void recordIngestion(long millis, int chunks) {
        registry.timer("rag.ingestion.latency").record(millis, TimeUnit.MILLISECONDS);
        registry.counter("rag.ingestion.chunks").increment(chunks);
    }

    public Timer.Sample startSample() {
        return Timer.start(registry);
    }

    public double estimateChatCost(int inputTokens, int outputTokens) {
        return (inputTokens / 1000.0) * cost.chatInputPer1kTokens()
                + (outputTokens / 1000.0) * cost.chatOutputPer1kTokens();
    }
}
