package com.testcase.rag_implement.llm;

import com.testcase.rag_implement.config.RagProperties;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResiliencePolicyTest {

    private RagProperties props(int maxAttempts, int failureThreshold) {
        return new RagProperties("openai", "refusal",
                new RagProperties.Embedding(1536, 32, "", ""),
                new RagProperties.Chunking(1000, 150),
                new RagProperties.Retrieval(5, 0.6),
                new RagProperties.Conversation(6, 1500),
                new RagProperties.Upload(20_971_520L),
                new RagProperties.Llm(30, new RagProperties.Llm.Retry(maxAttempts, 1, 1.0),
                        new RagProperties.Llm.CircuitBreaker(failureThreshold, 60_000)),
                new RagProperties.Cost(0.00002, 0.00015, 0.0006));
    }

    @Test
    void retriesTransientFailuresThenSucceeds() {
        ResiliencePolicy policy = new ResiliencePolicy(props(3, 10));
        AtomicInteger attempts = new AtomicInteger();

        String result = policy.execute("chat", () -> {
            if (attempts.incrementAndGet() < 2) {
                throw new RuntimeException("transient");
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    void wrapsFailureAsProviderExceptionAfterRetries() {
        ResiliencePolicy policy = new ResiliencePolicy(props(2, 10));
        assertThatThrownBy(() -> policy.execute("chat", () -> {
            throw new RuntimeException("boom");
        })).isInstanceOf(LlmProviderException.class);
    }

    @Test
    void circuitOpensAfterConsecutiveFailures() {
        ResiliencePolicy policy = new ResiliencePolicy(props(1, 3));
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> policy.execute("chat", () -> {
                throw new RuntimeException("boom");
            })).isInstanceOf(LlmProviderException.class);
        }
        assertThat(policy.circuitBreaker().state()).isEqualTo(CircuitBreaker.State.OPEN);

        // While open, calls are rejected without invoking the supplier.
        AtomicInteger invoked = new AtomicInteger();
        assertThatThrownBy(() -> policy.execute("chat", () -> {
            invoked.incrementAndGet();
            return "x";
        })).isInstanceOf(LlmProviderException.class);
        assertThat(invoked.get()).isZero();
    }
}
