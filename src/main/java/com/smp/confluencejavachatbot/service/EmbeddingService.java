package com.smp.confluencejavachatbot.service;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingService {

    private static final int MAX_EMBED_TEXT_CHARS = 6000;

    private final EmbeddingModel embeddingModel;

    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Text cannot be blank for embedding");
        }
        String normalized = text.trim();
        if (normalized.length() > MAX_EMBED_TEXT_CHARS) {
            normalized = normalized.substring(0, MAX_EMBED_TEXT_CHARS);
        }
        return embeddingModel.embed(normalized);
    }
}

