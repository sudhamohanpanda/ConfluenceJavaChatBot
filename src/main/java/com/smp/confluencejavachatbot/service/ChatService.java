package com.smp.confluencejavachatbot.service;

import com.smp.confluencejavachatbot.dto.SearchResultItem;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatService {

    private final ChatClient chatClient;

    public ChatService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public String chat(String userMessage, String context) {
        String systemPrompt = """
                You are a helpful assistant that answers questions based on provided context.
                Use the context below to answer the user's question.
                If the context does not contain information needed to answer the question, say so.
                
                Context:
                %s
                """.formatted(context);

        return this.chatClient
                .prompt()
                .system(systemPrompt)
                .user(userMessage)
                .call()
                .content();
    }

    public String summarizeSearchResults(String userQuery, List<SearchResultItem> results) {
        if (results == null || results.isEmpty()) {
            return "I could not find a strong match for your query. Please try a more specific question or provide a rootPageId filter.";
        }

        StringBuilder contextBuilder = new StringBuilder();
        for (SearchResultItem item : results) {
            contextBuilder.append("PageId: ").append(item.pageId()).append("\n");
            contextBuilder.append("Source URL: ").append(item.sourceUrl()).append("\n");
            contextBuilder.append("Title: ").append(item.title()).append("\n");
            contextBuilder.append("Content: ").append(item.content()).append("\n\n");
        }

        String systemPrompt = """
                You are a concise RAG assistant.
                Summarize the search results in 5 to 10 lines.
                Keep it factual and only use provided context.
                End with this exact phrase followed by the best source URL: Extracted information from URL
                Mention the most relevant source URL explicitly.
                """;

        String userPrompt = """
                User query: %s

                Search context:
                %s
                """.formatted(userQuery, contextBuilder);

        return this.chatClient
                .prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();
    }
}

