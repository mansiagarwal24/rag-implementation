package com.testcase.rag_implement.ingestion;

import com.testcase.rag_implement.config.RagProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Fixed-size character chunking with overlap, applied <em>within each page</em> so a chunk
 * never spans two pages and its page citation stays accurate.
 *
 * <p>Strategy rationale (see README): fixed-size with overlap is predictable, cheap, and
 * robust across the heterogeneous policy documents in scope (tables, short clauses, prose).
 * Overlap preserves context across chunk boundaries so a fact split near an edge is still
 * retrievable. Chunks prefer to break on whitespace to avoid cutting words mid-token.
 */
@Component
public class Chunker {

    private final int maxChars;
    private final int overlapChars;

    public Chunker(RagProperties props) {
        this.maxChars = props.chunking().maxChars();
        this.overlapChars = props.chunking().overlapChars();
    }

    public List<Chunk> chunk(List<ExtractedPage> pages) {
        List<Chunk> chunks = new ArrayList<>();
        int index = 0;
        for (ExtractedPage page : pages) {
            String normalized = normalize(page.text());
            if (normalized.isEmpty()) {
                continue;
            }
            for (String piece : splitWithOverlap(normalized)) {
                chunks.add(new Chunk(index++, page.pageNumber(), piece, TokenEstimator.estimate(piece)));
            }
        }
        return chunks;
    }

    private List<String> splitWithOverlap(String text) {
        List<String> pieces = new ArrayList<>();
        if (text.length() <= maxChars) {
            pieces.add(text);
            return pieces;
        }
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxChars, text.length());
            // Prefer to break on whitespace near the window end to avoid splitting words.
            if (end < text.length()) {
                int lastSpace = text.lastIndexOf(' ', end);
                if (lastSpace > start + (maxChars / 2)) {
                    end = lastSpace;
                }
            }
            String piece = text.substring(start, end).trim();
            if (!piece.isEmpty()) {
                pieces.add(piece);
            }
            if (end >= text.length()) {
                break;
            }
            // Step forward, retaining overlap for continuity.
            start = Math.max(end - overlapChars, start + 1);
        }
        return pieces;
    }

    private String normalize(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }
}
