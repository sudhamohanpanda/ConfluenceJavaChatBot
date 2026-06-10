# Confluence RAG Chatbot Implementation Plan

## Checklist
- [x] Define target architecture for Python UI + Spring Boot backend
- [x] Include Atlassian MCP in the integration strategy
- [x] Cover Confluence ingestion flow from `pageId` through child pages
- [x] Cover HTML-to-text conversion, chunking, embeddings, and pgvector storage
- [x] Cover retrieval/search endpoint design with `pageId` traceability
- [x] Recommend an embedding model
- [x] Provide phased delivery plan, risks, and operational guidance
- [x] Keep this plan implementation-focused without adding code

---

## 1. Objective

Build a multi-layer Confluence chatbot platform with the following goals:

1. A **Python-based frontend/UI** for indexing and chat interactions.
2. A **Spring Boot + Java backend** that:
   - fetches Confluence page content by `pageId`
   - recursively collects all child pages
   - stores page HTML and metadata
   - converts HTML to clean text
   - splits content into overlapping chunks
   - generates embeddings using OpenAI
   - stores vectors in PostgreSQL with pgvector
   - exposes REST APIs for ingestion and semantic retrieval
3. A **RAG-ready retrieval layer** that returns source-aware results including:
   - `pageId`
   - page title
   - Confluence URL
   - chunk text/snippet
   - similarity score
   - page hierarchy or parent relationship
4. Optional later enhancement: generate final answers using an LLM grounded on retrieved chunks.

---

## 2. High-Level Architecture

## 2.1 Logical Layers

### A. Frontend Layer — Python UI
Responsibilities:
- trigger ingestion for a given Confluence `pageId`
- view ingestion status
- submit user questions
- display search results and source references
- optionally display generated answer + supporting chunks

Suggested UI options:
- **Streamlit** for fastest prototype
- **FastAPI + Jinja/React frontend** for a more production-oriented UI

Recommended first step:
- Start with **Streamlit** for quick validation and stakeholder demo
- Move to a richer web UI only after retrieval quality is validated

### B. Backend Layer — Spring Boot + Spring AI
Responsibilities:
- Confluence connectivity
- ingestion orchestration
- HTML processing
- chunk generation
- embedding generation
- vector persistence in PostgreSQL/pgvector
- retrieval/search APIs
- optional answer synthesis using LLM

### C. Data Layer — PostgreSQL + pgvector
Responsibilities:
- store page metadata
- store cleaned text
- store chunk records
- store embeddings
- execute vector similarity search
- track ingestion jobs and statuses

### D. Model Layer — OpenAI via Spring AI
Responsibilities:
- generate embeddings for chunks and user queries
- optionally generate final natural language answers using retrieved context

---

## 3. Technology Recommendations

## 3.1 Backend
- **Java**: Java 21 recommended for stability and long-term support
- **Spring Boot**: stable GA version compatible with your Spring AI target
- **Spring AI**: for OpenAI integration and vector workflow support
- **Spring Web**: REST APIs
- **Spring Data JDBC / JPA**: metadata persistence
- **PostgreSQL + pgvector**: vector database
- **Jsoup**: HTML cleanup and text extraction
- **Jackson**: JSON mapping for Confluence API/MCP responses
- **Validation + Actuator**: operational visibility

## 3.2 Frontend
- **Python 3.11+**
- **Streamlit** for first version UI
- Optional: **FastAPI** if you want API-backed Python UI services

## 3.3 Embedding Model Recommendation

### Recommended starting model
**OpenAI `text-embedding-3-small`**

Why this is the best starting point:
- lower cost than larger models
- very strong retrieval performance for most enterprise RAG scenarios
- fast enough for bulk ingestion workloads
- good balance between quality and indexing throughput

### When to use `text-embedding-3-large`
Upgrade only if:
- retrieval precision is not good enough
- content is highly technical and semantically dense
- you need better ranking quality across large document sets

### Practical recommendation
Start with:
- **Embedding model**: `text-embedding-3-small`
- **Chat/answer model** later: a cost-effective OpenAI chat model suitable for grounded answers

---

## 4. Atlassian MCP Strategy

## 4.1 Why include Atlassian MCP
Atlassian MCP can be included as a **preferred integration adapter** for Confluence access because it may simplify tool-mediated access to Atlassian content and may align well with AI-assisted workflows.

## 4.2 Recommended architectural position for MCP
Do **not** hardwire the system exclusively to MCP.
Instead, create a **Confluence access abstraction layer** with two possible adapters:

1. **Atlassian MCP adapter**
2. **Direct Confluence REST API adapter**

This design gives you flexibility because MCP suitability can vary based on:
- deployment environment
- authentication mode
- API completeness
- paging and recursion support
- operational control requirements
- rate limit behavior

## 4.3 Recommended decision
Use the following strategy:

### Primary design
Implement a common interface such as:
- fetch page by `pageId`
- fetch page HTML/content body
- fetch child pages
- fetch page metadata

### Adapter option A: Atlassian MCP
Use this if it provides:
- reliable authentication for your Confluence instance
- page lookup by `pageId`
- recursive or repeatable child page traversal
- access to rendered HTML or storage-format content
- stable operational behavior for batch ingestion

### Adapter option B: Direct Confluence REST API
Keep this as:
- mandatory fallback
- likely production-safe default if MCP is insufficient for bulk indexing

## 4.4 Final recommendation on MCP
Include MCP in the plan as a **first-class optional connector**, but architect the backend so that **switching between MCP and REST is configuration-driven**.

This is the safest enterprise design.

---

## 5. Functional Scope

## 5.1 Step 1 — Download content by `pageId`, including child pages, with HTML and page IDs
Input:
- root `pageId`

Expected behavior:
1. Retrieve the root page from Confluence
2. Retrieve page metadata:
   - `pageId`
   - title
   - space key/name if available
   - URL/web link
   - parent page info if available
   - version/update timestamp if available
3. Retrieve page content in HTML or storage-rendered form
4. Discover child pages
5. Recursively fetch all descendants
6. Preserve each page independently
7. Maintain parent-child relationship for traceability

Expected output of this stage:
- one record per Confluence page
- original HTML for each page
- metadata including `pageId`
- hierarchy mapping

## 5.2 Step 2 — Convert HTML to text
For each fetched page:
1. parse HTML safely
2. remove navigation noise and non-content clutter where possible
3. preserve meaningful structure such as:
   - headings
   - bullet lists
   - numbered lists
   - table content where practical
   - code blocks
4. convert content to clean text
5. normalize whitespace
6. preserve enough line structure to support line-based overlap chunking

Expected output of this stage:
- `cleanText` per page
- optional `normalizedText`
- text suitable for chunking

## 5.3 Step 3 — Split text into chunks with 10-line overlap
For each page’s text:
1. split text into lines after normalization
2. create chunks using a line-based strategy
3. preserve overlap of **10 lines** between consecutive chunks
4. retain metadata per chunk:
   - `pageId`
   - chunk index
   - page title
   - page URL
   - start line
   - end line
   - parent page ID
   - root page ID

Important note:
You asked for 10-line overlap. That should be implemented literally as:
- if chunk N ends at line X,
- chunk N+1 begins at line `X - 9`

This approach is simple, explainable, and traceable.

### Recommended chunking design
Use a **hybrid size policy**:
- primary chunk boundary by line count
- soft guardrail for character length or token length

Reason:
- purely line-based chunking can produce chunks that are too small or too large
- a line-based overlap can still be maintained while enforcing practical chunk sizes

### Suggested starting chunk policy
- target: roughly 40–80 lines per chunk depending on average line size
- overlap: 10 lines
- max size guardrail: based on character count or token estimate

This should be tuned using retrieval tests.

## 5.4 Step 4 — Convert chunks to vectors and store in PostgreSQL
For each chunk:
1. send chunk text to embedding model
2. receive embedding vector
3. persist vector to PostgreSQL using pgvector
4. persist chunk metadata and source references

Expected stored data per chunk:
- chunk ID
- `pageId`
- root page ID
- parent page ID
- page title
- Confluence URL
- chunk index
- chunk text
- embedding vector
- line range
- ingestion timestamp
- checksum/hash for deduplication
- source type = Confluence

## 5.5 Step 5 — Expose backend as REST endpoint for UI
The backend must provide APIs that the Python UI can call for:
- ingestion request
- ingestion status
- semantic search
- optional answer generation
- source page inspection

---

## 6. End-to-End Flow

## 6.1 Ingestion Flow
1. User enters a Confluence `pageId` in the Python UI
2. UI calls backend ingestion API
3. Backend creates an ingestion job record
4. Backend fetches the requested page and descendants using MCP or REST adapter
5. Backend stores raw HTML and metadata for each page
6. Backend converts HTML to clean text
7. Backend chunks the text with 10-line overlap
8. Backend generates embeddings for each chunk
9. Backend stores chunks and vectors in PostgreSQL/pgvector
10. Backend updates ingestion status as completed or failed
11. UI displays summary:
   - pages indexed
   - chunks created
   - failures if any

## 6.2 Retrieval Flow
1. User enters a question in the Python UI
2. UI calls backend search endpoint
3. Backend converts query to embedding vector
4. Backend performs vector similarity search in pgvector
5. Backend returns top matching chunks with metadata
6. UI displays:
   - matched snippets
   - page title
   - `pageId`
   - Confluence URL
   - similarity score

## 6.3 Optional Answer Generation Flow
1. Query embedding finds top relevant chunks
2. Backend assembles retrieval context
3. Backend sends grounded prompt to chat model
4. Backend returns:
   - final answer
   - supporting sources
   - `pageId` list
   - confidence or score indicators if desired

---

## 7. Backend Component Design

## 7.1 Suggested backend modules

### A. API Layer
Responsibilities:
- request validation
- response formatting
- endpoint versioning
- error handling

### B. Orchestration Layer
Responsibilities:
- manage ingestion workflow
- coordinate fetch → transform → chunk → embed → persist
- track job status and retries

### C. Confluence Access Layer
Responsibilities:
- abstract MCP and REST integrations
- fetch page data and child pages
- normalize source response into a common page model

### D. Content Processing Layer
Responsibilities:
- HTML cleanup
- text normalization
- line preservation
- chunk generation

### E. Embedding Layer
Responsibilities:
- model selection
- embedding generation
- retry and backoff handling
- batching if supported and appropriate

### F. Retrieval Layer
Responsibilities:
- query embedding generation
- similarity search
- re-ranking if required later
- response assembly with metadata

### G. Persistence Layer
Responsibilities:
- relational metadata storage
- vector storage
- ingestion state tracking

---

## 8. Confluence Access Design

## 8.1 Common source model
Normalize both MCP and REST responses into a common internal model containing:
- `pageId`
- title
- URL
- space key
- parent page ID
- root page ID
- HTML body
- last modified timestamp
- version number
- child page references

## 8.2 Recursion strategy
The ingestion service should:
1. start with the supplied root `pageId`
2. fetch the page
3. fetch immediate children
4. recursively continue until leaf pages are reached
5. prevent cycles or duplicate processing

## 8.3 Safety controls
Include:
- max recursion depth configuration
- max pages per ingestion job
- rate limiting and retry policy
- deduplication by `pageId`
- timeout handling

## 8.4 Incremental ingestion support
For later phases, support:
- reindex page if updated
- skip unchanged pages based on version/timestamp/hash
- update embeddings only when content changed

---

## 9. HTML to Text Conversion Plan

## 9.1 Challenges specific to Confluence HTML
Confluence content may include:
- rich formatting
- macros
- tables
- expand/collapse regions
- code blocks
- user mentions
- inline attachments
- navigation and decorative markup

## 9.2 Processing goals
The conversion should preserve semantic meaning while reducing noise.

## 9.3 Recommended normalization rules
Preserve:
- headings
- section ordering
- list items
- paragraphs
- code/preformatted blocks
- table text flattened into readable lines

Remove or reduce:
- decorative wrappers
- empty nodes
- repetitive navigation fragments
- presentation-only markup

Normalize:
- repeated whitespace
- line breaks
- HTML entities
- hidden or script content

## 9.4 Output quality goals
The final text should be:
- readable by humans
- stable for chunking
- rich enough for embedding
- source-traceable to original page

---

## 10. Chunking Strategy

## 10.1 Why chunking matters
Chunking determines retrieval quality. Too small loses context; too large dilutes relevance.

## 10.2 Proposed chunking policy
Primary method:
- line-based chunking after HTML-to-text conversion

Required overlap:
- **10 lines**

Recommended enhancements:
- prevent splitting headings from the paragraph immediately below them when possible
- preserve code block boundaries when reasonable
- attach page title to chunk metadata
- optionally prepend section heading context to each chunk for better retrieval

## 10.3 Chunk metadata
Each chunk should carry:
- chunk ID
- `pageId`
- root page ID
- parent page ID
- page title
- Confluence URL
- chunk sequence number
- line start
- line end
- text length
- checksum/hash
- ingestion job ID

## 10.4 Future improvements
Later improvements may include:
- semantic chunking
- heading-aware chunking
- paragraph-aware chunking
- query-time re-ranking

For version 1, keep chunking deterministic and explainable.

---

## 11. PostgreSQL + pgvector Data Design

## 11.1 Core tables

### A. `confluence_page`
Purpose:
Store one record per fetched Confluence page.

Suggested fields:
- internal ID
- `page_id`
- root page ID
- parent page ID
- title
- URL
- space key
- raw HTML
- clean text
- source version
- content hash
- created timestamp
- updated timestamp
- last indexed timestamp

### B. `confluence_chunk`
Purpose:
Store chunked page content and metadata.

Suggested fields:
- internal chunk ID
- `page_id`
- root page ID
- parent page ID
- page title
- URL
- chunk index
- line start
- line end
- chunk text
- chunk hash
- embedding vector
- token estimate or char count
- ingestion job ID
- created timestamp

### C. `ingestion_job`
Purpose:
Track ingestion requests and operational status.

Suggested fields:
- job ID
- requested root `pageId`
- connector type used (`MCP` or `REST`)
- status
- total pages discovered
- total pages indexed
- total chunks created
- started at
- ended at
- error summary

## 11.2 Indexing strategy
Include indexes for:
- `page_id`
- root page ID
- parent page ID
- job ID
- updated timestamps
- vector similarity index for pgvector

## 11.3 Retention considerations
Decide whether to keep:
- full raw HTML forever
- only latest page version
- only latest chunk set per page

Recommended for early stage:
- keep latest HTML + latest chunk set
- optionally maintain historical versions later if auditability is required

---

## 12. REST API Plan

## 12.1 Ingestion APIs

### A. Start ingestion
Purpose:
Trigger recursive indexing for a root `pageId`.

Input should include:
- `pageId`
- optional connector preference: `MCP`, `REST`, or `AUTO`
- optional depth limit
- optional force reindex flag

Response should include:
- job ID
- accepted status
- root `pageId`
- selected connector

### B. Get ingestion status
Purpose:
Check progress of a submitted ingestion job.

Response should include:
- job ID
- status
- pages discovered
- pages processed
- chunks created
- failures
- timestamps

### C. Get indexed page summary
Purpose:
View what was stored for a page.

Response should include:
- `pageId`
- title
- URL
- child count
- chunk count
- last indexed timestamp

## 12.2 Retrieval APIs

### A. Semantic search
Purpose:
Convert user query to vector and return top matching chunks.

Input should include:
- `query`
- optional top K
- optional root page filter
- optional page ID filter
- optional score threshold

Response should include a list of matches with:
- `pageId`
- page title
- Confluence URL
- chunk text/snippet
- chunk index
- score
- line range
- parent page ID
- root page ID

### B. Optional grounded answer endpoint
Purpose:
Return a synthesized answer plus citations.

Input should include:
- `query`
- optional top K
- optional generation mode

Response should include:
- answer text
- supporting source list
- each source’s `pageId`
- chunk references
- page title and URL

## 12.3 Operational APIs
Potentially add:
- reindex page
- delete indexed content by root page ID
- health check
- model/config diagnostics

---

## 13. Python UI Plan

## 13.1 Core screens

### A. Ingestion screen
Capabilities:
- enter root `pageId`
- choose connector mode: `AUTO`, `MCP`, `REST`
- start ingestion
- show job status and summary

### B. Search/chat screen
Capabilities:
- enter user question
- show ranked results
- show source metadata
- allow click-through to Confluence URL
- display `pageId` clearly for reference

### C. Admin/status screen
Capabilities:
- recent jobs
- ingestion statistics
- error summaries
- indexed page counts

## 13.2 UX recommendations
Show the following in retrieval results:
- page title
- `pageId`
- similarity score
- short snippet
- “Open in Confluence” link
- root page lineage if available

This will improve trust and explainability.

---

## 14. Source Traceability Requirements

This is critical because the user explicitly wants returned results to contain `pageId`.

Every indexed chunk and every retrieval result should include:
- `pageId`
- page title
- Confluence URL
- chunk index
- line range
- parent page ID
- root page ID

Optional but useful:
- last updated date
- Confluence space key
- page breadcrumb/path

This guarantees every answer can be traced back to a Confluence source.

---

## 15. Security and Configuration Plan

## 15.1 Secrets management
Store securely:
- OpenAI API key
- Confluence or MCP credentials/tokens
- PostgreSQL credentials

Do not hardcode secrets in source or properties files committed to source control.

## 15.2 Backend configuration groups
Define configuration for:
- Confluence connector mode
- MCP settings
- REST API base URL and auth
- OpenAI model names
- PostgreSQL connection
- chunking parameters
- ingestion limits
- retrieval top K and thresholds

## 15.3 Access control
For enterprise readiness, protect ingestion endpoints so not every user can trigger bulk indexing.

---

## 16. Error Handling and Resilience

## 16.1 Ingestion failure scenarios
Handle:
- invalid `pageId`
- permission denied from Confluence
- empty page content
- child page fetch failure
- rate limits
- embedding API failure
- DB write failure
- partial ingestion completion

## 16.2 Recovery approach
Recommended behavior:
- continue indexing other pages if one child page fails, where possible
- record page-level failure details
- mark job as partial success when appropriate
- allow retry or reindex

## 16.3 Idempotency
A repeated request for the same `pageId` should not create uncontrolled duplication.

Use:
- content hash checks
- upsert semantics
- chunk replacement strategy per page version

---

## 17. Observability and Monitoring

Track metrics such as:
- pages fetched per job
- chunks created per job
- embedding calls count
- embedding latency
- query latency
- vector search latency
- failure counts by connector type
- MCP vs REST success rate

Log with correlation identifiers:
- ingestion job ID
- root `pageId`
- connector type

---

## 18. Delivery Phases

## Phase 1 — Foundation
Goal:
Prepare backend skeleton and data infrastructure.

Deliverables:
- Spring Boot project setup refinement
- PostgreSQL and pgvector connectivity
- configuration structure
- basic page/chunk/job data model
- health checks

## Phase 2 — Confluence ingestion
Goal:
Fetch page HTML and child pages recursively.

Deliverables:
- common Confluence connector interface
- REST adapter
- MCP adapter if feasible in your environment
- recursive page discovery
- raw HTML storage

## Phase 3 — Content processing
Goal:
Convert HTML into high-quality text and chunk it.

Deliverables:
- HTML cleanup pipeline
- normalized text generation
- 10-line overlap chunking
- chunk metadata generation

## Phase 4 — Embeddings and vector storage
Goal:
Generate vectors and persist them in pgvector.

Deliverables:
- embedding integration via Spring AI
- vector persistence
- deduplication and reindex logic

## Phase 5 — Search API
Goal:
Search indexed chunks by semantic similarity.

Deliverables:
- query embedding flow
- vector similarity search
- result ranking and metadata response

## Phase 6 — Python UI
Goal:
Provide a usable interface for ingestion and search.

Deliverables:
- page ingestion form
- job status view
- query interface
- result display with `pageId` and source links

## Phase 7 — Answer generation (optional)
Goal:
Add grounded answer synthesis.

Deliverables:
- retrieval + prompt assembly
- answer with citations
- optional answer mode toggle

---

## 19. Recommended MVP Scope

For the first usable release, include only:

1. Ingestion by root `pageId`
2. Recursive child page fetch
3. HTML storage
4. HTML-to-text conversion
5. 10-line overlap chunking
6. Embedding generation with `text-embedding-3-small`
7. pgvector storage
8. Semantic search endpoint
9. Python UI showing top matches with `pageId`

Do **not** make the first version too ambitious.

The fastest path to value is:
- high-quality ingestion
- correct traceability
- reliable search results

LLM answer generation can come next.

---

## 20. Recommended Answer Strategy for Later

Once retrieval is stable, add a second mode:

### Mode 1 — Search only
Return top relevant chunks with source metadata.

### Mode 2 — Search + Answer
Return:
- generated answer
- source chunks used
- `pageId` for each cited chunk

This dual mode is useful because:
- some users want direct evidence only
- others want a synthesized answer with citations

---

## 21. Risks and Mitigations

## 21.1 MCP capability mismatch
Risk:
Atlassian MCP may not fully support bulk recursive ingestion in the exact way required.

Mitigation:
- use MCP as optional adapter
- keep direct REST adapter fully supported

## 21.2 Poor retrieval due to noisy text
Risk:
Confluence HTML may contain noise that hurts embedding quality.

Mitigation:
- strong HTML cleanup
- preserve headings and section context
- test on representative pages

## 21.3 Chunk quality issues
Risk:
too-small or too-large chunks reduce retrieval quality

Mitigation:
- tune line count and max size policy
- validate with real queries

## 21.4 Cost growth
Risk:
embedding many pages can become expensive

Mitigation:
- start with `text-embedding-3-small`
- deduplicate unchanged content
- reindex incrementally

## 21.5 Traceability gaps
Risk:
users may not trust answers if sources are unclear

Mitigation:
- include `pageId`, title, URL, chunk index, and score in every result

---

## 22. Testing Strategy

## 22.1 Ingestion tests
Validate:
- valid page fetch
- child page recursion
- duplicate page prevention
- error handling for missing pages
- permission failure behavior

## 22.2 Content processing tests
Validate:
- HTML-to-text cleanup quality
- preservation of headings and lists
- table flattening behavior
- chunk overlap correctness

## 22.3 Embedding/vector tests
Validate:
- vector creation success
- vector storage success
- search returns expected relevant chunks

## 22.4 API tests
Validate:
- ingestion endpoint contract
- status endpoint contract
- search endpoint contract
- source metadata presence in responses

## 22.5 UI tests
Validate:
- ingestion form flow
- query display flow
- page link and `pageId` visibility

---

## 23. Suggested Success Criteria

The MVP should be considered successful when all of the following are true:

1. A user can submit a Confluence `pageId`
2. The backend fetches that page and all child pages
3. HTML is stored with metadata including `pageId`
4. Clean text is generated reliably
5. Chunks are created with exactly 10-line overlap
6. Embeddings are generated and stored in pgvector
7. A query can retrieve relevant chunks from the vector database
8. Every returned result includes `pageId` and source URL
9. Python UI can trigger ingestion and display search results clearly

---

## 24. Final Recommendation

### Architecture recommendation
Use the following production-friendly design:
- **Frontend**: Python UI, initially Streamlit
- **Backend**: Spring Boot + Spring AI
- **Vector store**: PostgreSQL + pgvector
- **Embeddings**: OpenAI `text-embedding-3-small`
- **Confluence access**: connector abstraction supporting both **Atlassian MCP** and **direct Confluence REST API**

### Connector recommendation
- Include **Atlassian MCP** in the plan and support it as an integration option
- Keep **direct REST API** as a required fallback and likely stable production default if MCP does not fully meet recursive ingestion needs

### MVP recommendation
Build the first release around:
- ingestion
- processing
- vectorization
- retrieval with `pageId` traceability

Then add answer generation after retrieval quality is proven.

---

## 25. Next Execution Plan After Approval

Once this plan is approved, the implementation work should proceed in this order:

1. finalize backend dependency stack and version alignment
2. define database schema for pages, chunks, and jobs
3. implement Confluence connector abstraction
4. implement REST connector first
5. evaluate and add MCP adapter where suitable
6. implement recursive ingestion by `pageId`
7. implement HTML-to-text conversion
8. implement chunking with 10-line overlap
9. integrate OpenAI embeddings through Spring AI
10. store vectors in pgvector
11. expose ingestion and search endpoints
12. build Python UI for ingestion and retrieval
13. add optional grounded answer generation

---

## 26. Decision Summary

### Best embedding model to start with
- **OpenAI `text-embedding-3-small`**

### Best UI starting point
- **Streamlit**

### Best backend approach
- **Spring Boot + Spring AI + PostgreSQL/pgvector**

### Best Confluence integration approach
- **Support Atlassian MCP, but do not depend on it exclusively**
- **Keep direct Confluence REST API as fallback/default-ready connector**

### Best first delivery scope
- **Searchable RAG index with source references before answer generation**

---

If needed later, this plan can be expanded into:
- a detailed API contract document
- a database schema design document
- a class/module design document for the Spring Boot backend
- a phased task breakdown for implementation sprint planning

