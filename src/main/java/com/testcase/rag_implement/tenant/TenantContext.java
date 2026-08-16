package com.testcase.rag_implement.tenant;

/**
 * Holds the current tenant id for the duration of a request on a ThreadLocal.
 * Every data-access path reads the tenant from here so a request can never
 * accidentally touch another tenant's data.
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(String tenantId) {
        CURRENT.set(tenantId);
    }

    public static String get() {
        return CURRENT.get();
    }

    /** Returns the tenant id or throws if none is bound (defensive; the filter enforces presence). */
    public static String require() {
        String tenantId = CURRENT.get();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("No tenant bound to the current context");
        }
        return tenantId;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
