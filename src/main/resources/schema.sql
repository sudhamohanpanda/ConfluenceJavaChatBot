CREATE SCHEMA IF NOT EXISTS chatbot;

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS chatbot.ingestion_job (
    id BIGSERIAL PRIMARY KEY,
    root_page_id VARCHAR(64) NOT NULL,
    connector_mode VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    pages_processed INTEGER NOT NULL DEFAULT 0,
    chunks_created INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS chatbot.confluence_page (
    id BIGSERIAL PRIMARY KEY,
    page_id VARCHAR(64) UNIQUE NOT NULL,
    root_page_id VARCHAR(64) NOT NULL,
    parent_page_id VARCHAR(64),
    title TEXT NOT NULL,
    source_url TEXT,
    space_key VARCHAR(64),
    html_content TEXT NOT NULL,
    text_content TEXT NOT NULL,
    updated_at_source TIMESTAMPTZ,
    last_indexed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS chatbot.confluence_chunk (
    id BIGSERIAL PRIMARY KEY,
    page_id VARCHAR(64) NOT NULL,
    root_page_id VARCHAR(64) NOT NULL,
    parent_page_id VARCHAR(64),
    title TEXT NOT NULL,
    source_url TEXT,
    chunk_index INTEGER NOT NULL,
    line_start INTEGER NOT NULL,
    line_end INTEGER NOT NULL,
    content TEXT NOT NULL,
    embedding vector NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_page_page_id ON chatbot.confluence_page(page_id);
CREATE INDEX IF NOT EXISTS idx_page_root_page_id ON chatbot.confluence_page(root_page_id);
CREATE INDEX IF NOT EXISTS idx_chunk_page_id ON chatbot.confluence_chunk(page_id);
CREATE INDEX IF NOT EXISTS idx_chunk_root_page_id ON chatbot.confluence_chunk(root_page_id);

