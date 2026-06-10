package com.smp.confluencejavachatbot.service;

import com.smp.confluencejavachatbot.config.AppProperties;
import com.smp.confluencejavachatbot.dto.IngestionReportResponse;
import com.smp.confluencejavachatbot.dto.PageLineReport;
import com.smp.confluencejavachatbot.repository.ChatbotRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class IngestionReportService {

    private final ChatbotRepository chatbotRepository;
    private final TextChunker textChunker;
    private final AppProperties appProperties;

    public IngestionReportService(ChatbotRepository chatbotRepository, TextChunker textChunker, AppProperties appProperties) {
        this.chatbotRepository = chatbotRepository;
        this.textChunker = textChunker;
        this.appProperties = appProperties;
    }

    public IngestionReportResponse buildReport(String rootPageId) {
        if (!StringUtils.hasText(rootPageId)) {
            throw new IllegalArgumentException("rootPageId is required");
        }

        List<PageLineReport> pages = chatbotRepository.listPagesByRootPageId(rootPageId)
                .stream()
                .map(page -> new PageLineReport(
                        page.pageId(),
                        page.title(),
                        textChunker.countChunkableLines(page.textContent())
                ))
                .toList();

        String splitTechnique = "line-overlap chunking (lines-per-chunk="
                + appProperties.chunking().linesPerChunk()
                + ", overlap-lines="
                + appProperties.chunking().overlapLines()
                + ")";

        return new IngestionReportResponse(pages.size(), pages, splitTechnique);
    }
}

