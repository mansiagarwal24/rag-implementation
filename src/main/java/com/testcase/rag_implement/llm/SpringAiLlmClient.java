package com.testcase.rag_implement.llm;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Default chat client backed by the configured Spring AI provider.
 *
 * <p>Streaming returns the provider Flux directly so that cancelling the subscription
 * (client disconnect) propagates cancellation to the underlying HTTP call — no orphaned
 * upstream requests.
 */
@Component
public class SpringAiLlmClient implements LlmClient {

    private final ChatModel chatModel;
    private final ResiliencePolicy resilience;

    public SpringAiLlmClient(ChatModel chatModel, ResiliencePolicy resilience) {
        this.chatModel = chatModel;
        this.resilience = resilience;
    }

    @Override
    public LlmResult complete(String systemPrompt, String userPrompt) {
        return resilience.execute("chat", () -> {
            ChatResponse response = chatModel.call(buildPrompt(systemPrompt, userPrompt));
            String answer = response.getResult().getOutput().getText();
            int in = 0;
            int out = 0;
            if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                var usage = response.getMetadata().getUsage();
                in = safeInt((Number) usage.getPromptTokens());
                out = safeInt((Number) usage.getCompletionTokens());
            }
            return new LlmResult(answer == null ? "" : answer, in, out, model());
        });
    }

    @Override
    public Flux<String> stream(String systemPrompt, String userPrompt) {
        if (!resilience.circuitBreaker().allowRequest()) {
            return Flux.error(new LlmProviderException("Model provider circuit is open for chat stream"));
        }
        return chatModel.stream(buildPrompt(systemPrompt, userPrompt))
                .map(resp -> {
                    var text = resp.getResult() != null && resp.getResult().getOutput() != null
                            ? resp.getResult().getOutput().getText()
                            : null;
                    return text == null ? "" : text;
                })
                .filter(s -> !s.isEmpty())
                .doOnComplete(() -> resilience.circuitBreaker().recordSuccess())
                .doOnError(e -> resilience.circuitBreaker().recordFailure())
                .onErrorMap(e -> e instanceof LlmProviderException ? e
                        : new LlmProviderException("Streaming chat failed", e));
    }

    @Override
    public String model() {
        return chatModel.getClass().getSimpleName();
    }

    private Prompt buildPrompt(String systemPrompt, String userPrompt) {
        List<Message> messages = List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt));
        return new Prompt(messages);
    }

    private static int safeInt(Number value) {
        return value == null ? 0 : value.intValue();
    }
}
