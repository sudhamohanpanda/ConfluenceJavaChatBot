package com.smp.confluencejavachatbot.service;

import com.smp.confluencejavachatbot.repository.ChatbotRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class IngestionOptionsService {

    private final ChatbotRepository chatbotRepository;

    public IngestionOptionsService(ChatbotRepository chatbotRepository) {
        this.chatbotRepository = chatbotRepository;
    }

    public Map<String, Object> getOptions() {
        List<Map<String, String>> rootPages = chatbotRepository.listRootPageOptions()
                .stream()
                .map(item -> Map.of(
                        "rootPageId", item.rootPageId(),
                        "title", item.title() == null ? "Untitled" : item.title()
                ))
                .toList();
        List<String> spaceKeys = chatbotRepository.listSpaceKeys();

        String defaultRootPageId = rootPages.isEmpty() ? null : rootPages.get(0).get("rootPageId");
        String defaultSpaceKey = spaceKeys.isEmpty() ? null : spaceKeys.get(0);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rootPages", rootPages);
        payload.put("spaceKeys", spaceKeys);
        payload.put("defaultRootPageId", defaultRootPageId);
        payload.put("defaultSpaceKey", defaultSpaceKey);
        return payload;
    }
}

