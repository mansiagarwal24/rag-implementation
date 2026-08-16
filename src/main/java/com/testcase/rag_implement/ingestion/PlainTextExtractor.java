package com.testcase.rag_implement.ingestion;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** TXT and Markdown extraction. Content is treated as a single section (page 1). */
@Component
public class PlainTextExtractor implements TextExtractor {

    @Override
    public boolean supports(String extension) {
        return "txt".equals(extension) || "md".equals(extension) || "markdown".equals(extension);
    }

    @Override
    public List<ExtractedPage> extract(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        if (text.isBlank()) {
            return List.of();
        }
        return List.of(new ExtractedPage(1, text));
    }
}
