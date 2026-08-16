package com.testcase.rag_implement.exception;

/** Small hierarchy of client-facing exceptions mapped to HTTP status codes by the advice. */
public final class ApiExceptions {

    private ApiExceptions() {
    }

    /** 400 */
    public static class BadRequestException extends RuntimeException {
        public BadRequestException(String message) {
            super(message);
        }
    }

    /** 404 */
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }

    /** 413 */
    public static class PayloadTooLargeException extends RuntimeException {
        public PayloadTooLargeException(String message) {
            super(message);
        }
    }

    /** 415 */
    public static class UnsupportedFileTypeException extends RuntimeException {
        public UnsupportedFileTypeException(String message) {
            super(message);
        }
    }
}
