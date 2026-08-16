package com.testcase.rag_implement.llm;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Minimal thread-safe circuit breaker: CLOSED -> OPEN after N consecutive failures,
 * OPEN -> HALF_OPEN after a cooldown, HALF_OPEN -> CLOSED on success or back to OPEN on failure.
 * Kept intentionally small and dependency-free so its behaviour is easy to reason about and test.
 */
public class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final int failureThreshold;
    private final long openMillis;

    private volatile State state = State.CLOSED;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong openedAt = new AtomicLong();

    public CircuitBreaker(int failureThreshold, long openMillis) {
        this.failureThreshold = failureThreshold;
        this.openMillis = openMillis;
    }

    /** @return true if a call is allowed through right now. */
    public synchronized boolean allowRequest() {
        if (state == State.OPEN) {
            if (System.currentTimeMillis() - openedAt.get() >= openMillis) {
                state = State.HALF_OPEN;
                return true;
            }
            return false;
        }
        return true;
    }

    public synchronized void recordSuccess() {
        consecutiveFailures.set(0);
        state = State.CLOSED;
    }

    public synchronized void recordFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (state == State.HALF_OPEN || failures >= failureThreshold) {
            state = State.OPEN;
            openedAt.set(System.currentTimeMillis());
        }
    }

    public State state() {
        return state;
    }
}
