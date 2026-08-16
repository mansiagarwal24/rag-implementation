package com.testcase.rag_implement.ingestion;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.List;

/**
 * DOCX extraction. DOCX has no reliable page model without rendering, so we emit the
 * whole document as a single section (page 1). This trade-off is documented in the README.
 */
@Component
public class DocxTextExtractor implements TextExtractor {

    @Override
    public boolean supports(String extension) {
        return "docx".equals(extension);
    }

    @Override
    public List<ExtractedPage> extract(byte[] content) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            String text = extractor.getText();
            if (text == null || text.isBlank()) {
                return List.of();
            }
            return List.of(new ExtractedPage(1, text));
        }
    }
}
