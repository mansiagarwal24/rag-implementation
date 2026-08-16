package com.testcase.rag_implement.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Binds the correlation id and tenant id for every request into ThreadLocals + MDC,
 * and clears them afterwards. Runs first so downstream code always has context.
 *
 * <p>Tenant presence is validated in the controller/advice layer rather than here so
 * that public endpoints (Swagger, actuator) are not forced to send a tenant header.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RagRequestFilter extends OncePerRequestFilter {

    public static final String CORRELATION_HEADER = "X-Correlation-Id";
    public static final String TENANT_HEADER = "X-Tenant-Id";
    public static final String MDC_CORRELATION = "correlationId";
    public static final String MDC_TENANT = "tenantId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader(CORRELATION_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        String tenantId = request.getHeader(TENANT_HEADER);

        try {
            CorrelationId.set(correlationId);
            MDC.put(MDC_CORRELATION, correlationId);
            response.setHeader(CORRELATION_HEADER, correlationId);

            if (tenantId != null && !tenantId.isBlank()) {
                TenantContext.set(tenantId.trim());
                MDC.put(MDC_TENANT, tenantId.trim());
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            CorrelationId.clear();
            MDC.clear();
        }
    }
}
