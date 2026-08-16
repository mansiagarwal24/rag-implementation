package com.testcase.rag_implement.llm;

import com.testcase.rag_implement.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Wraps provider calls with a circuit breaker + bounded retries with exponential backoff.
 * Only used for blocking calls (embeddings, non-streaming chat). Streaming applies the
 * circuit breaker but not mid-stream retries.
 */
@Component
public class ResiliencePolicy {

    private static final Logger log = LoggerFactory.getLogger(ResiliencePolicy.class);

    private final RagProperties.Llm config;
    private final CircuitBreaker circuitBreaker;

    public ResiliencePolicy(RagProperties props) {
        this.config = props.llm();
        this.circuitBreaker = new CircuitBreaker(
                props.llm().circuitBreaker().failureThreshold(),
                props.llm().circuitBreaker().openMillis());
    }

    public CircuitBreaker circuitBreaker() {
        return circuitBreaker;
    }

    /** Execute a provider call with circuit-breaker gating and retries. */
    public <T> T execute(String operation, Supplier<T> call) {
        if (!circuitBreaker.allowRequest()) {
            throw new LlmProviderException("Model provider circuit is open for " + operation);
        }

        RagProperties.Llm.Retry retry = config.retry();
        long backoff = retry.initialBackoffMillis();
        RuntimeException last = null;

        for (int attempt = 1; attempt <= retry.maxAttempts(); attempt++) {
            try {
                T result = call.get();
                circuitBreaker.recordSuccess();
                return result;
            } catch (RuntimeException ex) {
                last = ex;
                circuitBreaker.recordFailure();
                log.warn("Provider call '{}' failed on attempt {}/{}: {}",
                        operation, attempt, retry.maxAttempts(), ex.getMessage());
                if (attempt < retry.maxAttempts() && circuitBreaker.allowRequest()) {
                    sleep(backoff);
                    backoff = (long) (backoff * retry.backoffMultiplier());
                } else {
                    break;
                }
            }
        }
        throw new LlmProviderException("Model provider call '" + operation + "' failed after retries", last);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmProviderException("Interrupted while backing off", e);
        }
    }
}
