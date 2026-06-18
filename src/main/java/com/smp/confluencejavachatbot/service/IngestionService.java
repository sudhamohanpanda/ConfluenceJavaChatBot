package com.smp.confluencejavachatbot.service;

import com.smp.confluencejavachatbot.config.AppProperties;
import com.smp.confluencejavachatbot.confluence.ConfluenceClient;
import com.smp.confluencejavachatbot.confluence.ConfluenceClientFactory;
import com.smp.confluencejavachatbot.confluence.ConfluencePageData;
import com.smp.confluencejavachatbot.dto.ConnectorMode;
import com.smp.confluencejavachatbot.dto.IngestionResponse;
import com.smp.confluencejavachatbot.dto.IngestionStatusResponse;
import com.smp.confluencejavachatbot.repository.ChatbotRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class IngestionService {

    private static final Logger logger = LoggerFactory.getLogger(IngestionService.class);
    private static final int MAX_EMBED_SPLIT_DEPTH = 4;

    private final ConfluenceClientFactory confluenceClientFactory;
    private final AppProperties appProperties;
    private final HtmlTextExtractor htmlTextExtractor;
    private final TextChunker textChunker;
    private final EmbeddingService embeddingService;
    private final ChatbotRepository chatbotRepository;
    private final TaskExecutor ingestionTaskExecutor;
    private final Executor embeddingTaskExecutor;

    public IngestionService(ConfluenceClientFactory confluenceClientFactory,
                            AppProperties appProperties,
                            HtmlTextExtractor htmlTextExtractor,
                            TextChunker textChunker,
                            EmbeddingService embeddingService,
                            ChatbotRepository chatbotRepository,
                            @Qualifier("ingestionTaskExecutor") TaskExecutor ingestionTaskExecutor,
                            @Qualifier("embeddingTaskExecutor") Executor embeddingTaskExecutor) {
        this.confluenceClientFactory = confluenceClientFactory;
        this.appProperties = appProperties;
        this.htmlTextExtractor = htmlTextExtractor;
        this.textChunker = textChunker;
        this.embeddingService = embeddingService;
        this.chatbotRepository = chatbotRepository;
        this.ingestionTaskExecutor = ingestionTaskExecutor;
        this.embeddingTaskExecutor = embeddingTaskExecutor;
    }

    public IngestionResponse ingest(String rootPageId, String spaceKey, ConnectorMode connectorMode) {
        ConfluenceClient client = confluenceClientFactory.resolveClient(connectorMode);
        String resolvedRootPageId = resolveRootPageId(rootPageId, spaceKey, client);
        ConnectorMode resolvedMode = connectorMode == null ? ConnectorMode.AUTO : connectorMode;
        long jobId = chatbotRepository.createIngestionJob(resolvedRootPageId, resolvedMode.name());

        try {
            ingestionTaskExecutor.execute(() -> runIngestionJob(jobId, resolvedRootPageId, client));
        } catch (Exception ex) {
            chatbotRepository.updateIngestionJobFailure(jobId, 0, 0, "Unable to schedule ingestion job: " + ex.getMessage());
            return new IngestionResponse(jobId, "FAILED", 0, 0, "Ingestion failed to start: " + ex.getMessage());
        }

        return new IngestionResponse(jobId, "RUNNING", 0, 0, "Ingestion started");
    }

    private void runIngestionJob(long jobId, String resolvedRootPageId, ConfluenceClient client) {
        int maxPages = appProperties.ingestion() == null ? 5000 : appProperties.ingestion().maxPages();
        int chunkInsertBatchSize = appProperties.ingestion() == null ? 100 : appProperties.ingestion().chunkInsertBatchSize();

        int pagesProcessed = 0;
        int chunksCreated = 0;

        try {
            ArrayDeque<PageToVisit> queue = new ArrayDeque<>();
            Set<String> visited = new HashSet<>();
            queue.add(new PageToVisit(resolvedRootPageId, null));

            while (!queue.isEmpty()) {
                if (pagesProcessed >= maxPages) {
                    throw new IllegalStateException("Ingestion aborted: max page limit reached (" + maxPages + ")");
                }

                PageToVisit current = queue.removeFirst();
                if (!visited.add(current.pageId())) {
                    continue;
                }

                ConfluencePageData page;
                try {
                    page = client.fetchPage(resolvedRootPageId, current.pageId(), current.parentPageId());
                } catch (Exception ex) {
                    if (isMissingPageError(ex)) {
                        logger.warn("Skipping page {} because it is missing or inaccessible: {}", current.pageId(), ex.getMessage());
                        continue;
                    }
                    throw ex;
                }
                String text = htmlTextExtractor.toText(page.htmlBody());
                List<TextChunker.Chunk> chunks = textChunker.chunk(text);
                EmbeddingBatchResult embeddingBatch = embedChunksConcurrently(chunks);

                chatbotRepository.replacePageChunks(
                        page,
                        text,
                        embeddingBatch.embeddedChunks(),
                        embeddingBatch.embeddings(),
                        chunkInsertBatchSize
                );
                chunksCreated += embeddingBatch.embeddedChunks().size();

                for (String childPageId : page.childPageIds()) {
                    queue.add(new PageToVisit(childPageId, page.pageId()));
                }

                pagesProcessed++;
            }

            chatbotRepository.updateIngestionJobSuccess(jobId, pagesProcessed, chunksCreated);
        } catch (Exception ex) {
            chatbotRepository.updateIngestionJobFailure(jobId, pagesProcessed, chunksCreated, ex.getMessage());
        }
    }

    public IngestionStatusResponse getStatus(long jobId) {
        return chatbotRepository.getIngestionStatus(jobId);
    }

    private String resolveRootPageId(String rootPageId, String spaceKey, ConfluenceClient client) {
        if (StringUtils.hasText(rootPageId)) {
            return rootPageId.trim();
        }
        if (!StringUtils.hasText(spaceKey)) {
            throw new IllegalArgumentException("Either pageId or spaceKey is required");
        }
        return client.resolveRootPageIdBySpaceKey(spaceKey.trim());
    }

    private List<EmbeddedChunk> embedChunkWithFallback(TextChunker.Chunk chunk, int depth) {
        try {
            return List.of(new EmbeddedChunk(chunk, embeddingService.embed(chunk.content())));
        } catch (RuntimeException ex) {
            if (!isContextLengthError(ex) || depth >= MAX_EMBED_SPLIT_DEPTH) {
                throw ex;
            }

            List<TextChunker.Chunk> splitChunks = splitChunkInHalf(chunk);
            if (splitChunks.size() <= 1) {
                throw ex;
            }

            List<EmbeddedChunk> resolved = new ArrayList<>();
            for (TextChunker.Chunk splitChunk : splitChunks) {
                resolved.addAll(embedChunkWithFallback(splitChunk, depth + 1));
            }
            return resolved;
        }
    }

    private EmbeddingBatchResult embedChunksConcurrently(List<TextChunker.Chunk> chunks) {
        if (chunks.isEmpty()) {
            return new EmbeddingBatchResult(List.of(), List.of());
        }

        List<CompletableFuture<List<EmbeddedChunk>>> futures = new ArrayList<>(chunks.size());
        for (TextChunker.Chunk chunk : chunks) {
            futures.add(CompletableFuture.supplyAsync(() -> embedChunkWithFallback(chunk, 0), embeddingTaskExecutor));
        }

        List<TextChunker.Chunk> embeddedChunks = new ArrayList<>();
        List<float[]> embeddings = new ArrayList<>();

        for (CompletableFuture<List<EmbeddedChunk>> future : futures) {
            List<EmbeddedChunk> resolvedChunks;
            try {
                resolvedChunks = future.join();
            } catch (CompletionException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw ex;
            }

            for (EmbeddedChunk resolvedChunk : resolvedChunks) {
                int chunkIndex = embeddedChunks.size();
                embeddedChunks.add(new TextChunker.Chunk(
                        chunkIndex,
                        resolvedChunk.chunk().lineStart(),
                        resolvedChunk.chunk().lineEnd(),
                        resolvedChunk.chunk().content()
                ));
                embeddings.add(resolvedChunk.embedding());
            }
        }

        return new EmbeddingBatchResult(embeddedChunks, embeddings);
    }

    private List<TextChunker.Chunk> splitChunkInHalf(TextChunker.Chunk chunk) {
        String[] lines = chunk.content().split("\\R");
        if (lines.length < 2) {
            String content = chunk.content();
            if (content.length() < 2) {
                return List.of(chunk);
            }
            int mid = content.length() / 2;
            String leftContent = content.substring(0, mid).trim();
            String rightContent = content.substring(mid).trim();
            if (leftContent.isEmpty() || rightContent.isEmpty()) {
                return List.of(chunk);
            }
            return List.of(
                    new TextChunker.Chunk(chunk.chunkIndex(), chunk.lineStart(), chunk.lineEnd(), leftContent),
                    new TextChunker.Chunk(chunk.chunkIndex(), chunk.lineStart(), chunk.lineEnd(), rightContent)
            );
        }

        int mid = lines.length / 2;
        String leftContent = String.join("\n", java.util.Arrays.copyOfRange(lines, 0, mid)).trim();
        String rightContent = String.join("\n", java.util.Arrays.copyOfRange(lines, mid, lines.length)).trim();
        if (leftContent.isEmpty() || rightContent.isEmpty()) {
            return List.of(chunk);
        }

        int leftLineStart = chunk.lineStart();
        int leftLineEnd = chunk.lineStart() + mid - 1;
        int rightLineStart = leftLineEnd + 1;
        int rightLineEnd = chunk.lineEnd();

        return List.of(
                new TextChunker.Chunk(chunk.chunkIndex(), leftLineStart, leftLineEnd, leftContent),
                new TextChunker.Chunk(chunk.chunkIndex(), rightLineStart, rightLineEnd, rightContent)
        );
    }

    private boolean isContextLengthError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase();
                if (normalized.contains("context length")
                        || normalized.contains("input length exceeds")
                        || normalized.contains("token") && normalized.contains("limit")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isMissingPageError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase();
                if (normalized.contains("404")
                        || normalized.contains("not found")
                        || normalized.contains("no content found with id")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private record EmbeddedChunk(TextChunker.Chunk chunk, float[] embedding) {
    }

    private record EmbeddingBatchResult(List<TextChunker.Chunk> embeddedChunks, List<float[]> embeddings) {
    }

    private record PageToVisit(String pageId, String parentPageId) {
    }
}

