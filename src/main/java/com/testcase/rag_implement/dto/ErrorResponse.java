package com.testcase.rag_implement.dto;

import java.time.Instant;

/** Consistent error body. Never contains stack traces or provider internals. */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String correlationId
) {
}
