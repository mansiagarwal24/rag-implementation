package com.testcase.rag_implement.entity;

/** Document ingestion lifecycle. Transitions: PROCESSING -> READY | FAILED. */
public enum DocumentStatus {
    PROCESSING,
    READY,
    FAILED
}
