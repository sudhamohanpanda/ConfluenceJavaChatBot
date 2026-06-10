package com.smp.confluencejavachatbot.service;

import com.smp.confluencejavachatbot.config.AppProperties;
import com.smp.confluencejavachatbot.dto.ConnectorMode;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextChunkerTest {

    @Test
    void shouldCreateChunksWithTenLineOverlap() {
        AppProperties properties = new AppProperties(
                ConnectorMode.REST,
                new AppProperties.Chunking(20, 10),
                new AppProperties.Ingestion(1000, 50),
                new AppProperties.Confluence("", "", "", ""),
                new AppProperties.Mcp("", "")
        );
        TextChunker chunker = new TextChunker(properties);

        String text = IntStream.rangeClosed(1, 35)
                .mapToObj(i -> "line-" + i)
                .reduce((a, b) -> a + "\n" + b)
                .orElseThrow();

        var chunks = chunker.chunk(text);

        assertEquals(3, chunks.size());
        assertEquals(1, chunks.get(0).lineStart());
        assertEquals(20, chunks.get(0).lineEnd());
        assertEquals(11, chunks.get(1).lineStart());
        assertEquals(30, chunks.get(1).lineEnd());
        assertEquals(21, chunks.get(2).lineStart());
        assertEquals(35, chunks.get(2).lineEnd());

        assertTrue(chunks.get(0).content().contains("line-10"));
        assertTrue(chunks.get(1).content().contains("line-11"));
        assertTrue(chunks.get(2).content().contains("line-35"));
    }
}

