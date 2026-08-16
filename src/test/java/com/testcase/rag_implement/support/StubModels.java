package com.testcase.rag_implement.support;

import com.testcase.rag_implement.llm.EmbeddingClient;
import com.testcase.rag_implement.llm.LlmClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deterministic, offline stand-ins for the model provider so the whole test suite runs
 * with no API key. The embedding is a normalized bag-of-words hash: texts sharing words
 * are close in cosine space, disjoint texts are near-orthogonal — enough to exercise
 * real retrieval, thresholds and the refusal path against a real pgvector database.
 */
@TestConfiguration
public class StubModels {

    public static final int DIMS = 1536;

    /** Counts calls to complete() so tests can assert the LLM is NOT invoked on refusal. */
    public static final AtomicInteger COMPLETE_CALLS = new AtomicInteger(0);

    @Bean
    @Primary
    public EmbeddingClient stubEmbeddingClient() {
        return new EmbeddingClient() {
            @Override
            public List<float[]> embed(List<String> texts) {
                return texts.stream().map(StubModels::hashEmbed).toList();
            }

            @Override
            public int dimensions() {
                return DIMS;
            }
        };
    }

    @Bean
    @Primary
    public LlmClient stubLlmClient() {
        return new LlmClient() {
            @Override
            public LlmResult complete(String systemPrompt, String userPrompt) {
                COMPLETE_CALLS.incrementAndGet();
                return new LlmResult("STUB_ANSWER grounded in provided context.", 10, 5, "stub-model");
            }

            @Override
            public Flux<String> stream(String systemPrompt, String userPrompt) {
                return Flux.just("STUB ", "ANSWER");
            }

            @Override
            public String model() {
                return "stub-model";
            }
        };
    }

    public static float[] hashEmbed(String text) {
        float[] vec = new float[DIMS];
        for (String token : text.toLowerCase().split("\\W+")) {
            if (token.isBlank()) {
                continue;
            }
            int idx = Math.floorMod(token.hashCode(), DIMS);
            vec[idx] += 1.0f;
        }
        double norm = 0;
        for (float v : vec) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < DIMS; i++) {
                vec[i] /= (float) norm;
            }
        }
        return vec;
    }
}
