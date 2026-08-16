package com.testcase.rag_implement.tenant;

/** Holds the correlation id for the current request on a ThreadLocal (mirrors MDC). */
public final class CorrelationId {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private CorrelationId() {
    }

    public static void set(String id) {
        CURRENT.set(id);
    }

    public static String get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
