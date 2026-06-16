package com.smp.confluencejavachatbot.repository;

import com.smp.confluencejavachatbot.confluence.ConfluencePageData;
import com.smp.confluencejavachatbot.dto.IngestionStatusResponse;
import com.smp.confluencejavachatbot.dto.SearchResultItem;
import com.smp.confluencejavachatbot.service.TextChunker;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Repository
public class ChatbotRepository {

    private final JdbcTemplate jdbcTemplate;

    public ChatbotRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long createIngestionJob(String rootPageId, String connectorMode) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO chatbot.ingestion_job(root_page_id, connector_mode, status)
                VALUES (?, ?, 'RUNNING')
                RETURNING id
                """,
                Long.class,
                rootPageId,
                connectorMode
        );
    }

    public void updateIngestionJobSuccess(long jobId, int pagesProcessed, int chunksCreated) {
        jdbcTemplate.update(
                """
                UPDATE chatbot.ingestion_job
                   SET status='COMPLETED', pages_processed=?, chunks_created=?, error_message=NULL, updated_at=NOW()
                 WHERE id=?
                """,
                pagesProcessed,
                chunksCreated,
                jobId
        );
    }

    public void updateIngestionJobFailure(long jobId, int pagesProcessed, int chunksCreated, String errorMessage) {
        jdbcTemplate.update(
                """
                UPDATE chatbot.ingestion_job
                   SET status='FAILED', pages_processed=?, chunks_created=?, error_message=?, updated_at=NOW()
                 WHERE id=?
                """,
                pagesProcessed,
                chunksCreated,
                errorMessage,
                jobId
        );
    }

    public void upsertPage(ConfluencePageData pageData, String textContent) {
        jdbcTemplate.update(
                """
                INSERT INTO chatbot.confluence_page (
                    page_id, root_page_id, parent_page_id, title, source_url, space_key, html_content, text_content,
                    updated_at_source, last_indexed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (page_id) DO UPDATE SET
                    root_page_id=EXCLUDED.root_page_id,
                    parent_page_id=EXCLUDED.parent_page_id,
                    title=EXCLUDED.title,
                    source_url=EXCLUDED.source_url,
                    space_key=EXCLUDED.space_key,
                    html_content=EXCLUDED.html_content,
                    text_content=EXCLUDED.text_content,
                    updated_at_source=EXCLUDED.updated_at_source,
                    last_indexed_at=NOW()
                """,
                pageData.pageId(),
                pageData.rootPageId(),
                pageData.parentPageId(),
                pageData.title(),
                pageData.sourceUrl(),
                pageData.spaceKey(),
                pageData.htmlBody(),
                textContent,
                pageData.sourceUpdatedAt() == null ? null : Timestamp.from(pageData.sourceUpdatedAt().toInstant())
        );
    }

    public void deleteChunksByPageId(String pageId) {
        jdbcTemplate.update("DELETE FROM chatbot.confluence_chunk WHERE page_id=?", pageId);
    }

    public void insertChunk(ConfluencePageData pageData, TextChunker.Chunk chunk, float[] embedding) {
        jdbcTemplate.update(
                """
                INSERT INTO chatbot.confluence_chunk(
                    page_id, root_page_id, parent_page_id, title, source_url,
                    chunk_index, line_start, line_end, content, embedding
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS vector))
                """,
                pageData.pageId(),
                pageData.rootPageId(),
                pageData.parentPageId(),
                pageData.title(),
                pageData.sourceUrl(),
                chunk.chunkIndex(),
                chunk.lineStart(),
                chunk.lineEnd(),
                chunk.content(),
                toVectorLiteral(embedding)
        );
    }

    @Transactional
    public void replacePageChunks(ConfluencePageData pageData,
                                  String textContent,
                                  List<TextChunker.Chunk> chunks,
                                  List<float[]> embeddings,
                                  int batchSize) {
        if (chunks.size() != embeddings.size()) {
            throw new IllegalArgumentException("chunks and embeddings size mismatch");
        }

        upsertPage(pageData, textContent);
        deleteChunksByPageId(pageData.pageId());

        if (chunks.isEmpty()) {
            return;
        }

        int effectiveBatchSize = Math.max(batchSize, 1);
        for (int offset = 0; offset < chunks.size(); offset += effectiveBatchSize) {
            int end = Math.min(offset + effectiveBatchSize, chunks.size());
            List<Object[]> args = new ArrayList<>(end - offset);
            for (int i = offset; i < end; i++) {
                TextChunker.Chunk chunk = chunks.get(i);
                args.add(new Object[]{
                        pageData.pageId(),
                        pageData.rootPageId(),
                        pageData.parentPageId(),
                        pageData.title(),
                        pageData.sourceUrl(),
                        chunk.chunkIndex(),
                        chunk.lineStart(),
                        chunk.lineEnd(),
                        chunk.content(),
                        toVectorLiteral(embeddings.get(i))
                });
            }
            jdbcTemplate.batchUpdate(
                    """
                    INSERT INTO chatbot.confluence_chunk(
                        page_id, root_page_id, parent_page_id, title, source_url,
                        chunk_index, line_start, line_end, content, embedding
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS vector))
                    """,
                    args
            );
        }
    }

    public List<StoredPage> listPagesByRootPageId(String rootPageId) {
        return jdbcTemplate.query(
                """
                SELECT page_id, title, text_content
                  FROM chatbot.confluence_page
                 WHERE root_page_id = ?
                 ORDER BY page_id
                """,
                (rs, rowNum) -> new StoredPage(
                        rs.getString("page_id"),
                        rs.getString("title"),
                        rs.getString("text_content")
                ),
                rootPageId
        );
    }

    public List<RootPageOption> listRootPageOptions() {
        return jdbcTemplate.query(
                """
                SELECT cp.root_page_id,
                       COALESCE(rp.title, MIN(cp.title)) AS title,
                       MAX(cp.last_indexed_at) AS latest_indexed_at
                  FROM chatbot.confluence_page cp
                  LEFT JOIN chatbot.confluence_page rp ON rp.page_id = cp.root_page_id
                 GROUP BY cp.root_page_id, rp.title
                 ORDER BY latest_indexed_at DESC
                """,
                (rs, rowNum) -> new RootPageOption(
                        rs.getString("root_page_id"),
                        rs.getString("title")
                )
        );
    }

    public List<String> listSpaceKeys() {
        return jdbcTemplate.queryForList(
                """
                SELECT DISTINCT space_key
                  FROM chatbot.confluence_page
                 WHERE space_key IS NOT NULL
                   AND space_key <> ''
                 ORDER BY space_key
                """,
                String.class
        );
    }

    public IngestionStatusResponse getIngestionStatus(long jobId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT id, root_page_id, connector_mode, status, pages_processed, chunks_created,
                       error_message, created_at, updated_at
                  FROM chatbot.ingestion_job
                 WHERE id = ?
                """,
                ingestionStatusRowMapper(),
                jobId
        );
    }

    public List<SearchResultItem> search(String queryEmbedding, int topK, String rootPageId) {
        if (StringUtils.hasText(rootPageId)) {
            return jdbcTemplate.query(
                    """
                    SELECT page_id, root_page_id, parent_page_id, title, source_url,
                           chunk_index, line_start, line_end, content,
                           (1 - (embedding <=> CAST(? AS vector))) AS similarity_score
                      FROM chatbot.confluence_chunk
                     WHERE root_page_id = ?
                     ORDER BY embedding <=> CAST(? AS vector)
                     LIMIT ?
                    """,
                    searchRowMapper(),
                    queryEmbedding,
                    rootPageId,
                    queryEmbedding,
                    topK
            );
        }

        return jdbcTemplate.query(
                """
                SELECT page_id, root_page_id, parent_page_id, title, source_url,
                       chunk_index, line_start, line_end, content,
                       (1 - (embedding <=> CAST(? AS vector))) AS similarity_score
                  FROM chatbot.confluence_chunk
                 ORDER BY embedding <=> CAST(? AS vector)
                 LIMIT ?
                """,
                searchRowMapper(),
                queryEmbedding,
                queryEmbedding,
                topK
        );
    }

    public static String toVectorLiteral(float[] vector) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(String.format(Locale.US, "%f", vector[i]));
        }
        return builder.append(']').toString();
    }

    public record StoredPage(String pageId, String title, String textContent) {
    }

    public record RootPageOption(String rootPageId, String title) {
    }

    private RowMapper<IngestionStatusResponse> ingestionStatusRowMapper() {
        return (rs, rowNum) -> new IngestionStatusResponse(
                rs.getLong("id"),
                rs.getString("root_page_id"),
                rs.getString("connector_mode"),
                rs.getString("status"),
                rs.getInt("pages_processed"),
                rs.getInt("chunks_created"),
                rs.getString("error_message"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private RowMapper<SearchResultItem> searchRowMapper() {
        return (rs, rowNum) -> new SearchResultItem(
                rs.getString("page_id"),
                rs.getString("root_page_id"),
                rs.getString("parent_page_id"),
                rs.getString("title"),
                rs.getString("source_url"),
                rs.getInt("chunk_index"),
                rs.getInt("line_start"),
                rs.getInt("line_end"),
                rs.getString("content"),
                rs.getDouble("similarity_score")
        );
    }
}

