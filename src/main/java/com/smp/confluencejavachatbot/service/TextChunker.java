package com.smp.confluencejavachatbot.service;

import com.smp.confluencejavachatbot.config.AppProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TextChunker {

    private final AppProperties appProperties;

    public TextChunker(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public List<Chunk> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        int linesPerChunk = appProperties.chunking().linesPerChunk();
        int overlapLines = appProperties.chunking().overlapLines();
        if (linesPerChunk <= overlapLines) {
            throw new IllegalStateException("lines-per-chunk must be greater than overlap-lines");
        }

        List<String> lines = toNonBlankLines(text);

        if (lines.isEmpty()) {
            return List.of();
        }

        List<Chunk> chunks = new ArrayList<>();
        int step = linesPerChunk - overlapLines;
        int chunkIndex = 0;

        for (int start = 0; start < lines.size(); start += step) {
            int endExclusive = Math.min(start + linesPerChunk, lines.size());
            List<String> chunkLines = lines.subList(start, endExclusive);
            String content = String.join("\n", chunkLines).trim();
            if (!content.isEmpty()) {
                chunks.add(new Chunk(chunkIndex, start + 1, endExclusive, content));
                chunkIndex++;
            }
            if (endExclusive == lines.size()) {
                break;
            }
        }

        return chunks;
    }

    public int countChunkableLines(String text) {
        return toNonBlankLines(text).size();
    }

    private List<String> toNonBlankLines(String text) {
        String[] rawLines = text.split("\\R");
        List<String> lines = new ArrayList<>(rawLines.length);
        for (String rawLine : rawLines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }
        return lines;
    }

    public record Chunk(int chunkIndex, int lineStart, int lineEnd, String content) {
    }
}

