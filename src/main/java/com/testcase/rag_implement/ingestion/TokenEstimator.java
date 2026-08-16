package com.testcase.rag_implement.ingestion;

/**
 * Cheap, provider-independent token estimate (~4 chars per token for English).
 * Good enough for chunk sizing, history budgeting and cost estimates without pulling
 * in a model-specific tokenizer. Documented as an approximation in the README.
 */
public final class TokenEstimator {

    private TokenEstimator() {
    }

    public static int estimate(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(text.length() / 4.0));
    }
}
