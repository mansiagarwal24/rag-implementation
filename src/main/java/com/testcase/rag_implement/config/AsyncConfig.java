package com.testcase.rag_implement.config;

import com.testcase.rag_implement.tenant.CorrelationId;
import com.testcase.rag_implement.tenant.RagRequestFilter;
import com.testcase.rag_implement.tenant.TenantContext;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Ingestion runs off the HTTP thread on a bounded executor (NFR-3). The queue is
 * bounded and the rejection policy is CallerRuns, so a flood of uploads applies
 * backpressure instead of exhausting memory with an unbounded queue.
 *
 * <p>The {@link TaskDecorator} copies correlation id + tenant id across the async
 * boundary so ingestion logs carry the same context as the originating request.
 */
@Configuration
public class AsyncConfig {

    public static final String INGESTION_EXECUTOR = "ingestionExecutor";

    @Bean(INGESTION_EXECUTOR)
    public Executor ingestionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50); // bounded
        executor.setThreadNamePrefix("ingest-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /** Copies MDC + tenant/correlation ThreadLocals from the submitting thread to the worker thread. */
    static final class ContextPropagatingTaskDecorator implements TaskDecorator {
        @Override
        public Runnable decorate(Runnable runnable) {
            Map<String, String> mdc = MDC.getCopyOfContextMap();
            String tenant = TenantContext.get();
            String correlationId = CorrelationId.get();
            return () -> {
                try {
                    if (mdc != null) {
                        MDC.setContextMap(mdc);
                    }
                    if (tenant != null) {
                        TenantContext.set(tenant);
                        MDC.put(RagRequestFilter.MDC_TENANT, tenant);
                    }
                    if (correlationId != null) {
                        CorrelationId.set(correlationId);
                        MDC.put(RagRequestFilter.MDC_CORRELATION, correlationId);
                    }
                    runnable.run();
                } finally {
                    MDC.clear();
                    TenantContext.clear();
                    CorrelationId.clear();
                }
            };
        }
    }
}
