package com.testcase.rag_implement.ingestion;

/** A single chunk produced by the chunker, tagged with its source page and index. */
public record Chunk(int chunkIndex, Integer pageNumber, String text, int tokenCount) {
}
