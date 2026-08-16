package com.testcase.rag_implement.exception;

import com.testcase.rag_implement.dto.ErrorResponse;
import com.testcase.rag_implement.llm.LlmProviderException;
import com.testcase.rag_implement.tenant.CorrelationId;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;

/**
 * Central error handling. Clients only ever see a clean JSON body with a code and message;
 * stack traces, API keys and provider internals never leak out.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiExceptions.BadRequestException.class)
    public ResponseEntity<ErrorResponse> badRequest(ApiExceptions.BadRequestException ex) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldError() != null
                ? ex.getBindingResult().getFieldError().getDefaultMessage()
                : "Validation failed";
        return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> missingHeader(MissingRequestHeaderException ex) {
        return build(HttpStatus.BAD_REQUEST, "MISSING_HEADER", "Missing required header: " + ex.getHeaderName());
    }

    @ExceptionHandler(ApiExceptions.NotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(ApiExceptions.NotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(ApiExceptions.UnsupportedFileTypeException.class)
    public ResponseEntity<ErrorResponse> unsupported(ApiExceptions.UnsupportedFileTypeException ex) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", ex.getMessage());
    }

    @ExceptionHandler({ApiExceptions.PayloadTooLargeException.class, MaxUploadSizeExceededException.class})
    public ResponseEntity<ErrorResponse> tooLarge(Exception ex) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE",
                "Uploaded file exceeds the maximum allowed size");
    }

    @ExceptionHandler(LlmProviderException.class)
    public ResponseEntity<ErrorResponse> provider(LlmProviderException ex) {
        // Log server-side with detail; return a clean message to the client.
        log.error("Model provider error: {}", ex.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE, "MODEL_PROVIDER_UNAVAILABLE",
                "The model provider is currently unavailable. Please retry shortly.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> generic(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred");
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message) {
        ErrorResponse body = new ErrorResponse(Instant.now(), status.value(), code, message, CorrelationId.get());
        return ResponseEntity.status(status).body(body);
    }
}
