package com.smp.confluencejavachatbot.controller;

import com.smp.confluencejavachatbot.dto.ChatRequest;
import com.smp.confluencejavachatbot.dto.ChatResponse;
import com.smp.confluencejavachatbot.dto.IngestionReportResponse;
import com.smp.confluencejavachatbot.dto.IngestionRequest;
import com.smp.confluencejavachatbot.dto.IngestionResponse;
import com.smp.confluencejavachatbot.dto.IngestionStatusResponse;
import com.smp.confluencejavachatbot.dto.SearchRequest;
import com.smp.confluencejavachatbot.dto.SearchResponse;
import com.smp.confluencejavachatbot.dto.SearchSummaryResponse;
import com.smp.confluencejavachatbot.service.ChatService;
import com.smp.confluencejavachatbot.service.IngestionReportService;
import com.smp.confluencejavachatbot.service.IngestionService;
import com.smp.confluencejavachatbot.service.SearchService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ChatbotController {

    private final IngestionService ingestionService;
    private final IngestionReportService ingestionReportService;
    private final SearchService searchService;
    private final ChatService chatService;

    public ChatbotController(IngestionService ingestionService,
                             IngestionReportService ingestionReportService,
                             SearchService searchService,
                             ChatService chatService) {
        this.ingestionService = ingestionService;
        this.ingestionReportService = ingestionReportService;
        this.searchService = searchService;
        this.chatService = chatService;
    }

    @PostMapping("/ingestion")
    public IngestionResponse startIngestion(@RequestBody IngestionRequest request) {
        if (request == null || !StringUtils.hasText(request.pageId())) {
            throw new IllegalArgumentException("pageId is required");
        }
        return ingestionService.ingest(request.pageId(), request.connectorMode());
    }

    @GetMapping("/ingestion/{jobId}")
    public IngestionStatusResponse getIngestionStatus(@PathVariable long jobId) {
        return ingestionService.getStatus(jobId);
    }

    @GetMapping("/ingestion/report/{rootPageId}")
    public IngestionReportResponse getIngestionReport(@PathVariable String rootPageId) {
        return ingestionReportService.buildReport(rootPageId);
    }

    @PostMapping("/search")
    public SearchSummaryResponse search(@RequestBody SearchRequest request) {
        if (request == null || !StringUtils.hasText(request.query())) {
            throw new IllegalArgumentException("query is required");
        }
        SearchResponse rawResponse = searchService.search(request);
        String summary = chatService.summarizeSearchResults(request.query(), rawResponse.results());
        return new SearchSummaryResponse(summary);
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        if (request == null || !StringUtils.hasText(request.query())) {
            throw new IllegalArgumentException("query is required");
        }

        // First, search for relevant documents
        SearchRequest searchRequest = new SearchRequest(request.query(), request.topK(), request.rootPageId());
        SearchResponse searchResponse = searchService.search(searchRequest);

        // Build context from search results
        StringBuilder contextBuilder = new StringBuilder();
        searchResponse.results().forEach(item -> {
            contextBuilder.append("Title: ").append(item.title()).append("\n");
            contextBuilder.append("Content: ").append(item.content()).append("\n\n");
        });

        // Generate response using LLM
        String response = chatService.chat(request.query(), contextBuilder.toString());

        return new ChatResponse(request.query(), response, searchResponse.results());
    }
}

