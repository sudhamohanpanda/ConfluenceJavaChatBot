# RAG Architecture Review Report

## 1) Scope and Objective

This report reviews the current Confluence RAG backend and UI implementation for:
- design quality,
- performance bottlenecks,
- SOLID adherence,
- concrete modernization/improvement actions,
- ingestion reporting requirements (pages found, per-page line counts, split technique).

Primary focus areas:
- speed,
- memory usage,
- readability.

Codebase reviewed includes:
- backend (`src/main/java/...`, `src/main/resources/...`),
- tests (`src/test/java/...`),
- python UI (`python-ui/app.py`).

---

## 2) Current Architecture (As Implemented)

### 2.1 High-level flow
1. User sends root `pageId` to `/api/v1/ingestion`.
2. `IngestionService` recursively crawls Confluence via `ConfluenceClient` (REST or MCP).
3. HTML is normalized to text (`HtmlTextExtractor`).
4. Text is split into line-overlap chunks (`TextChunker`).
5. Embeddings are generated (`EmbeddingService`).
6. Chunks + vectors are stored in PostgreSQL/pgvector (`ChatbotRepository`).
7. User query goes to `/api/v1/search` or `/api/v1/chat`.
8. Search does embedding + vector nearest-neighbor query + optional LLM summary/chat.

### 2.2 Main components
- API Layer:
  - `ChatbotController`
  - `ApiExceptionHandler`
- Ingestion and Retrieval Services:
  - `IngestionService`, `SearchService`, `ChatService`, `IngestionReportService`
- Processing Services:
  - `HtmlTextExtractor`, `TextChunker`, `EmbeddingService`
- Source Connector Abstraction:
  - `ConfluenceClient`, `RestConfluenceClient`, `McpConfluenceClient`, `ConfluenceClientFactory`
- Persistence:
  - `ChatbotRepository` using `JdbcTemplate`
  - schema in `src/main/resources/schema.sql`

### 2.3 Data model highlights
- `chatbot.ingestion_job`
- `chatbot.confluence_page`
- `chatbot.confluence_chunk` with `embedding vector`

---

## 3) Findings (Ordered by Severity)

## Critical

### C1. Plaintext secrets in local configuration
**Location:** `src/main/resources/application-local.yaml`
- Contains Confluence credentials and external API keys in plaintext.
- Risk: credential leakage, accidental commit/push, non-compliant operational posture.

**Impact:** Very high security and operational risk.

---

### C2. `/search` endpoint does not return match list/scores as required
**Location:** `src/main/java/com/smp/confluencejavachatbot/controller/ChatbotController.java`
- `/search` currently returns `SearchSummaryResponse` (summary text only).
- Requirement says return top matches and score percentage for all matches.
- `SearchResultItem` already contains `similarityScore`, but not surfaced by `/search` response.

**Impact:** Functional mismatch with product requirement.

---

### C3. Vector search is missing ANN index on embedding
**Location:** `src/main/resources/schema.sql`, `ChatbotRepository.search(...)`
- Current schema has btree indexes only on ID/root fields.
- No `ivfflat`/`hnsw` index on vector column.
- `ORDER BY embedding <=> CAST(? AS vector) LIMIT ?` likely degrades to expensive scans as corpus grows.

**Impact:** High query latency at scale.

---

## High

### H1. Ingestion is synchronous request/response
**Location:** `ChatbotController.startIngestion -> IngestionService.ingest`
- Ingestion executes in the HTTP request thread.
- Large trees block request thread for long duration.

**Impact:** Throughput degradation, poor resilience under concurrent ingestion.

---

### H2. `spaceKey` accepted by DTO/UI but not used by ingestion logic
**Location:**
- `IngestionRequest` has `spaceKey`,
- `python-ui/app.py` sends `spaceKey`,
- `ChatbotController/IngestionService` ignore `spaceKey`.

**Impact:** UX confusion and misleading API contract.

---

### H3. Embedding calls are strictly sequential
**Location:** `IngestionService` chunk embedding loop
- Embedding performed one chunk at a time.
- No batching and no controlled concurrency.

**Impact:** Slow ingestion for large page trees.

---

### H4. Full delete-and-reinsert chunk strategy on every page reindex
**Location:** `ChatbotRepository.replacePageChunks(...)`
- Upsert page, delete all existing chunks, insert all chunks again.
- Correct but expensive for minor updates.

**Impact:** Write amplification, extra I/O and CPU.

---

## Medium

### M1. Hot-path vector serialization cost
**Location:** `ChatbotRepository.toVectorLiteral(...)`
- Uses `String.format` per float in loops.
- This is CPU-expensive for high volume.

---

### M2. Generic error handler may leak internal error details
**Location:** `ApiExceptionHandler.handleGeneric`
- Returns exception message directly to client.

---

### M3. Search endpoint always invokes LLM summary path
**Location:** `/search` in `ChatbotController`
- Retrieval-only use cases pay model latency/cost.

---

### M4. API naming inconsistencies in report DTO
**Location:** `IngestionReportResponse`, `PageLineReport`
- Example: `total_number_ofpage`, `pageID`, `pageName` naming style mismatch.

---

## 4) Performance Bottlenecks and Their Root Causes

1. **Vector retrieval scale bottleneck**
   - Root cause: missing pgvector ANN index.
   - Symptoms: increased latency with corpus size.

2. **Ingestion throughput bottleneck**
   - Root cause: sequential embedding and synchronous ingestion execution.
   - Symptoms: long-running ingestion requests, poor parallelism.

3. **Database write pressure**
   - Root cause: full chunk replacement every reindex.
   - Symptoms: high write I/O and transaction cost.

4. **CPU overhead in vector serialization**
   - Root cause: string formatting in hot loop.

5. **Extra model calls for search summary**
   - Root cause: summary generation coupled to search endpoint.

---

## 5) SOLID Review

### Single Responsibility Principle (SRP)
- **Partial violation:** `IngestionService` combines orchestration, retry/fallback heuristics, chunk splitting fallback, and error classification.
- **Improvement:** split into orchestrator + embedding fallback strategy + error classifier.

### Open/Closed Principle (OCP)
- **Good:** connector abstraction is extensible (`ConfluenceClient`, factory, REST/MCP implementations).

### Liskov Substitution Principle (LSP)
- **Generally acceptable:** REST and MCP clients adhere to common contract.

### Interface Segregation Principle (ISP)
- **Acceptable:** source connector interface is compact.

### Dependency Inversion Principle (DIP)
- **Mostly good:** services depend on abstractions/components (`EmbeddingModel`, `ConfluenceClientFactory`).
- **Could improve:** response shaping responsibilities in controller can be separated into dedicated presenters/assemblers.

---

## 6) Requirement Fit Check

Requirement: user chooses root page, enters query, optional match count; system retrieves match(es) and provides match score percentage on all matches.

### Current status
- Optional topK is supported in request model and service clamping.
- Match scores are computed (`similarityScore`) at repository/query level.
- **Gap:** `/search` response currently returns only summary text, not the scored match list.

### Required contract evolution
- Provide retrieval response payload with:
  - `query`,
  - `topK`,
  - `results[]` including `similarityScore` and normalized `matchPercent`.
- Keep summary/chat as optional separate endpoint or flag.

---

## 7) Ingestion Reporting (Pages, Lines, Split Technique)

## 7.1 What code currently supports
- `IngestionReportService.buildReport(rootPageId)` returns:
  - total page count,
  - per-page line count,
  - split technique text from config.

## 7.2 Line counting method
- Non-blank trimmed lines only (`TextChunker.countChunkableLines`).

## 7.3 Split technique currently used
- `line-overlap chunking`
- configured by:
  - `app.chunking.lines-per-chunk` (currently 30)
  - `app.chunking.overlap-lines` (currently 5)

## 7.4 Observed ingestion run metrics (from current runtime)
- Root page ID: `755173627`
- Connector: `REST`
- Status: `COMPLETED`
- Pages processed: `428`
- Chunks created: `442`

Note:
- Per-page line details are available through `/api/v1/ingestion/report/{rootPageId}`.

---

## 8) Concrete Improvement Plan (Prioritized)

## Phase 1: Security and API correctness
1. Remove plaintext credentials from tracked files; use env vars/secret manager.
2. Update `/search` contract to return scored match list (and optional summary separately).
3. Stop returning raw internal exception messages in 500 responses.

## Phase 2: Retrieval speed
1. Add pgvector ANN index on `confluence_chunk.embedding`.
2. Validate query/operator uses cosine vs L2 consistently with model characteristics.
3. Add query performance benchmark script for realistic corpus size.

## Phase 3: Ingestion throughput
1. Convert ingestion to async job execution (non-blocking API).
2. Introduce embedding batching/controlled concurrency.
3. Add retry/backoff policy for transient model/network failures.

## Phase 4: Reindex efficiency
1. Store content hash/version and skip unchanged pages.
2. Delta update chunks instead of full delete/reinsert when possible.

## Phase 5: Contract cleanup and readability
1. If `spaceKey` is required behavior, implement it in service/connector.
2. If not required, remove from DTO/UI payload to avoid confusion.
3. Standardize DTO field naming and API schema style.

---

## 9) Proposed API Shape (Target)

## 9.1 Retrieval response
```json
{
  "query": "...",
  "topK": 5,
  "results": [
    {
      "pageId": "...",
      "rootPageId": "...",
      "title": "...",
      "chunkIndex": 0,
      "lineStart": 1,
      "lineEnd": 30,
      "similarityScore": 0.83,
      "matchPercent": 83.0,
      "content": "..."
    }
  ]
}
```

## 9.2 Optional summary endpoint
- `/api/v1/search/summary`
- Input: query + retrieval results or query + topK
- Output: compact natural-language summary only.

---

## 10) Memory and Readability Notes

### Memory
- Current flow builds full text and full chunk lists per page before persistence.
- For large pages, consider streaming chunk generation + immediate embedding + buffered batch writes.

### Readability
- Overall package organization is clear.
- Main readability issue is concentration of logic inside `IngestionService` and mixed concerns in `/search` endpoint behavior.

---

## 11) Summary

The project already has a solid RAG baseline with:
- connector abstraction,
- deterministic line-overlap chunking,
- vector persistence,
- end-to-end ingestion and retrieval.

The top issues to address next are:
1. security hardening of secrets,
2. retrieval contract alignment with score visibility,
3. vector index/performance at scale,
4. async and parallel ingestion pipeline.

Addressing these will significantly improve speed, memory profile under load, and maintainability/readability while aligning exactly with the stated product requirements.

