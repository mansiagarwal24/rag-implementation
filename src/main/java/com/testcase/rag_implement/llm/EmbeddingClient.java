package com.testcase.rag_implement.llm;

import java.util.List;

/** Provider-agnostic embedding abstraction. Batched by contract. */
public interface EmbeddingClient {

    /** Embed a batch of texts, returning one vector per input in order. */
    List<float[]> embed(List<String> texts);

    /** Embed a single text (convenience for query embedding). */
    default float[] embedOne(String text) {
        return embed(List.of(text)).get(0);
    }

    int dimensions();
}
