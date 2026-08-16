package com.testcase.rag_implement.ingestion;

import com.testcase.rag_implement.config.RagProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for chunking boundary behaviour. No Spring context required. */
class ChunkerTest {

    private Chunker chunker(int maxChars, int overlap) {
        RagProperties props = new RagProperties(
                "openai", "refusal",
                new RagProperties.Embedding(1536, 32, "", ""),
                new RagProperties.Chunking(maxChars, overlap),
                new RagProperties.Retrieval(5, 0.6),
                new RagProperties.Conversation(6, 1500),
                new RagProperties.Upload(20_971_520L),
                new RagProperties.Llm(30, new RagProperties.Llm.Retry(3, 500, 2.0),
                        new RagProperties.Llm.CircuitBreaker(5, 15000)),
                new RagProperties.Cost(0.00002, 0.00015, 0.0006));
        return new Chunker(props);
    }

    @Test
    void emptyFileProducesNoChunks() {
        List<Chunk> chunks = chunker(100, 20).chunk(List.of(new ExtractedPage(1, "")));
        assertThat(chunks).isEmpty();
    }

    @Test
    void blankWhitespaceProducesNoChunks() {
        List<Chunk> chunks = chunker(100, 20).chunk(List.of(new ExtractedPage(1, "   \n\t  ")));
        assertThat(chunks).isEmpty();
    }

    @Test
    void singleWordProducesOneChunk() {
        List<Chunk> chunks = chunker(100, 20).chunk(List.of(new ExtractedPage(1, "hello")));
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).text()).isEqualTo("hello");
        assertThat(chunks.get(0).pageNumber()).isEqualTo(1);
        assertThat(chunks.get(0).tokenCount()).isGreaterThan(0);
    }

    @Test
    void textSmallerThanChunkSizeStaysSingle() {
        String text = "The late fee for term two is five hundred rupees.";
        List<Chunk> chunks = chunker(1000, 150).chunk(List.of(new ExtractedPage(3, text)));
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).pageNumber()).isEqualTo(3);
    }

    @Test
    void textLargerThanChunkSizeSplitsWithOverlap() {
        String word = "policy ";
        String text = word.repeat(100); // ~700 chars
        List<Chunk> chunks = chunker(200, 50).chunk(List.of(new ExtractedPage(1, text)));
        assertThat(chunks.size()).isGreaterThan(1);
        // Chunk indexes are contiguous and start at 0.
        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).chunkIndex()).isEqualTo(i);
            assertThat(chunks.get(i).text().length()).isLessThanOrEqualTo(200);
        }
    }

    @Test
    void chunksNeverSpanTwoPages() {
        List<Chunk> chunks = chunker(50, 10).chunk(List.of(
                new ExtractedPage(1, "aaaa ".repeat(30)),
                new ExtractedPage(2, "bbbb ".repeat(30))));
        assertThat(chunks).anyMatch(c -> c.pageNumber() == 1);
        assertThat(chunks).anyMatch(c -> c.pageNumber() == 2);
        // No chunk mixes page-1 and page-2 content.
        assertThat(chunks).noneMatch(c -> c.text().contains("aaaa") && c.text().contains("bbbb"));
    }
}
