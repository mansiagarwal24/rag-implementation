package com.testcase.rag_implement.ingestion;

import java.util.List;

/** Extracts text from an uploaded file, preserving page/section positions. */
public interface TextExtractor {

    /** @return true if this extractor handles the given lowercase file extension. */
    boolean supports(String extension);

    List<ExtractedPage> extract(byte[] content) throws Exception;
}
