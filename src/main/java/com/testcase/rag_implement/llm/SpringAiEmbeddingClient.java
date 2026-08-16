package com.testcase.rag_implement.llm;

import com.testcase.rag_implement.config.RagProperties;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Default embedding client backed by whatever Spring AI provider is configured.
 * Swapping providers is a config change (starter + properties), not a code change.
 */
@Component
public class SpringAiEmbeddingClient implements EmbeddingClient {

    private final EmbeddingModel embeddingModel;
    private final ResiliencePolicy resilience;
    private final int dimensions;

    public SpringAiEmbeddingClient(EmbeddingModel embeddingModel, ResiliencePolicy resilience, RagProperties props) {
        this.embeddingModel = embeddingModel;
        this.resilience = resilience;
        this.dimensions = props.embedding().dimensions();
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }
        return resilience.execute("embed", () -> embeddingModel.embed(texts));
    }

    @Override
    public int dimensions() {
        return dimensions;
    }
}
