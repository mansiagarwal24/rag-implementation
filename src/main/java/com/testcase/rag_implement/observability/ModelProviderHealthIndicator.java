package com.testcase.rag_implement.observability;

import com.testcase.rag_implement.llm.CircuitBreaker;
import com.testcase.rag_implement.llm.ResiliencePolicy;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Reports model-provider health via the circuit-breaker state, so /actuator/health
 * degrades to DOWN when the provider has been failing (circuit OPEN) without making
 * a live billable call on every health probe.
 */
@Component("modelProvider")
public class ModelProviderHealthIndicator implements HealthIndicator {

    private final ResiliencePolicy resilience;

    public ModelProviderHealthIndicator(ResiliencePolicy resilience) {
        this.resilience = resilience;
    }

    @Override
    public Health health() {
        CircuitBreaker.State state = resilience.circuitBreaker().state();
        Health.Builder builder = state == CircuitBreaker.State.OPEN ? Health.down() : Health.up();
        return builder.withDetail("circuitState", state.name()).build();
    }
}
