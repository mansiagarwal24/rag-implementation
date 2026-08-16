package com.testcase.rag_implement.ingestion;

/**
 * Text of a single logical page/section. {@code pageNumber} is 1-based for PDFs;
 * for formats without pages (TXT/DOCX/MD) it is a best-effort section index.
 */
public record ExtractedPage(int pageNumber, String text) {
}
