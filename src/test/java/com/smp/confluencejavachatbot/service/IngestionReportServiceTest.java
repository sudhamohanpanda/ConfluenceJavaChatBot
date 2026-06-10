package com.smp.confluencejavachatbot.service;

import com.smp.confluencejavachatbot.config.AppProperties;
import com.smp.confluencejavachatbot.dto.ConnectorMode;
import com.smp.confluencejavachatbot.dto.IngestionReportResponse;
import com.smp.confluencejavachatbot.repository.ChatbotRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IngestionReportServiceTest {

    @Test
    void shouldBuildReportWithPageLineCountsAndSplitTechnique() {
        ChatbotRepository repository = mock(ChatbotRepository.class);
        AppProperties properties = new AppProperties(
                ConnectorMode.REST,
                new AppProperties.Chunking(20, 5),
                new AppProperties.Ingestion(1000, 50),
                new AppProperties.Confluence("", "", "", ""),
                new AppProperties.Mcp("", "")
        );

        when(repository.listPagesByRootPageId("root-1")).thenReturn(List.of(
                new ChatbotRepository.StoredPage("p1", "Page 1", "line-1\nline-2\n\nline-3"),
                new ChatbotRepository.StoredPage("p2", "Page 2", "single-line")
        ));

        IngestionReportService service = new IngestionReportService(repository, new TextChunker(properties), properties);
        IngestionReportResponse response = service.buildReport("root-1");

        assertEquals(2, response.totalNumberOfPage());
        assertEquals("p1", response.pages().get(0).pageID());
        assertEquals(3, response.pages().get(0).numberOfLines());
        assertEquals(1, response.pages().get(1).numberOfLines());
        assertTrue(response.splitTechnique().contains("lines-per-chunk=20"));
        assertTrue(response.splitTechnique().contains("overlap-lines=5"));
    }
}

