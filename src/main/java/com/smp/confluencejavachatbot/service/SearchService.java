package com.smp.confluencejavachatbot.service;

import com.smp.confluencejavachatbot.dto.SearchRequest;
import com.smp.confluencejavachatbot.dto.SearchResponse;
import com.smp.confluencejavachatbot.dto.SearchResultItem;
import com.smp.confluencejavachatbot.repository.ChatbotRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SearchService {

    private static final int DEFAULT_TOP_K = 2;
    private static final int MAX_TOP_K = 10;

    private final EmbeddingService embeddingService;
    private final ChatbotRepository chatbotRepository;

    public SearchService(EmbeddingService embeddingService, ChatbotRepository chatbotRepository) {
        this.embeddingService = embeddingService;
        this.chatbotRepository = chatbotRepository;
    }

    public SearchResponse search(SearchRequest request) {
        int requestedTopK = request.topK() == null ? DEFAULT_TOP_K : request.topK();
        int topK = Math.max(1, Math.min(requestedTopK, MAX_TOP_K));
        float[] vector = embeddingService.embed(request.query());
        String queryVector = ChatbotRepository.toVectorLiteral(vector);

        List<SearchResultItem> results = chatbotRepository.search(queryVector, topK, request.rootPageId());
        return new SearchResponse(request.query(), topK, results, null);
    }
}

