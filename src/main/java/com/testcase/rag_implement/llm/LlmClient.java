package com.testcase.rag_implement.llm;

import reactor.core.publisher.Flux;

/** Provider-agnostic chat abstraction. Business code depends only on this. */
public interface LlmClient {

    /** Blocking completion used by the non-streaming chat endpoint. */
    LlmResult complete(String systemPrompt, String userPrompt);

    /**
     * Streaming completion. Tokens are emitted as they arrive. Cancelling the returned
     * Flux (client disconnect) must cancel the upstream provider request.
     */
    Flux<String> stream(String systemPrompt, String userPrompt);

    /** Identifier of the currently configured chat model, for logging/metrics. */
    String model();

    /** Answer plus token accounting for metrics/cost. */
    record LlmResult(String answer, int inputTokens, int outputTokens, String model) {
    }
}
