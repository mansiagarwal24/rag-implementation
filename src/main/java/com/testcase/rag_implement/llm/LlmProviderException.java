package com.testcase.rag_implement.llm;

/** Raised when the model/embedding provider fails. Surfaces to the client as 503. */
public class LlmProviderException extends RuntimeException {

    public LlmProviderException(String message, Throwable cause) {
        super(message, cause);
    }

    public LlmProviderException(String message) {
        super(message);
    }
}
