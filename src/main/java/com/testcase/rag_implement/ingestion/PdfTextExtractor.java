package com.testcase.rag_implement.ingestion;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** PDF extraction with page numbers preserved (one ExtractedPage per PDF page). */
@Component
public class PdfTextExtractor implements TextExtractor {

    @Override
    public boolean supports(String extension) {
        return "pdf".equals(extension);
    }

    @Override
    public List<ExtractedPage> extract(byte[] content) throws Exception {
        List<ExtractedPage> pages = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            // Sort text by position so multi-column / tabular layouts keep a sensible
            // reading order instead of being interleaved. Helps (but does not fully solve)
            // table extraction — see README "Known Limitations".
            stripper.setSortByPosition(true);
            int total = document.getNumberOfPages();
            for (int page = 1; page <= total; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document);
                if (text != null && !text.isBlank()) {
                    pages.add(new ExtractedPage(page, text));
                }
            }
        }
        return pages;
    }
}
