# Document Q&A Assistant (RAG)

A backend service that ingests school policy documents (PDF, DOCX, TXT, Markdown) and answers
natural-language questions about them using Retrieval-Augmented Generation, with citations back
to the source document and page. Built for multi-tenant use, with grounded answers and an
explicit refusal path when the documents don't contain the answer.

---

## 1. Overview

**Problem.** School administrators keep a folder of documents — fee policy, transport rules, exam
circulars, HR leave policy, admission SOPs — and answer the same questions from them repeatedly.

**Solution.** Upload documents once; ask questions in natural language. The service embeds the
documents into a pgvector store, retrieves the most relevant chunks for a question (filtered by
tenant and optional category at the database level), and asks an LLM to answer **only** from those
chunks. If nothing relevant is found, it refuses instead of guessing.

**High-level flow.**

```
Ingestion:  Upload → 202 (PROCESSING) → [async] extract text (page-aware)
            → chunk → batch-embed → single-tx insert chunks+embeddings → READY

Query:      Question → tenant resolve → query embedding → DB-level tenant/category
            + vector search → similarity threshold
                              ├─ nothing clears threshold → fixed refusal (no LLM call)
                              └─ top-K chunks → grounded prompt → LLM → answer + citations
                                 → persist conversation turn + message→source mapping
```

---

## 2. Tech stack

| Concern        | Choice                                             |
|----------------|----------------------------------------------------|
| Language       | Java 21 (LTS)                                       |
| Framework      | Spring Boot 4.1                                     |
| AI layer       | Spring AI 2.0                                        |
| LLM provider   | Local **Ollama** (chat: `qwen2.5:7b`, embed: `nomic-embed-text`) — swappable via config |
| Vector store   | PostgreSQL 16 + **pgvector** (HNSW index)           |
| Schema         | `schema.sql` run on startup (see note below)        |
| Build          | Maven (wrapper committed)                           |
| Tests          | JUnit 5, Testcontainers (real pgvector)             |
| API docs       | springdoc OpenAPI / Swagger UI                      |

> **Note on migrations.** This project initializes the schema directly via
> `src/main/resources/schema.sql` (idempotent `CREATE ... IF NOT EXISTS`) rather than Flyway/
> Liquibase. See [Known Limitations](#17-known-limitations) — the assignment expects a migration
> tool, and this is a deliberate, documented deviation.

---

## 3. Prerequisites

- **Java 21** (`java -version`).
- **Docker** (only needed to run Postgres+pgvector conveniently, and to run the integration tests).
- **Ollama** running locally with the two models pulled:
  ```
  ollama pull qwen2.5:7b
  ollama pull nomic-embed-text
  ```
- A **PostgreSQL 16 with the pgvector extension**. Easiest via Docker:
  ```
  docker run --name rag-pg -e POSTGRES_DB=ragdb -e POSTGRES_USER=raguser \
    -e POSTGRES_PASSWORD=ragpassword -p 5432:5432 -d pgvector/pgvector:pg16
  ```

No API keys are required (the LLM is local). No secrets are committed.

---

## 4. Configuration

All configuration lives in `application.yml` and is overridable via environment variables. Copy
`.env.example` for reference. Key variables:

| Variable                        | Default                | Meaning |
|---------------------------------|------------------------|---------|
| `SPRING_DATASOURCE_URL`         | `jdbc:postgresql://localhost:5432/ragdb` | DB URL |
| `SPRING_DATASOURCE_USERNAME`    | `raguser`              | DB user |
| `SPRING_DATASOURCE_PASSWORD`    | `ragpassword`          | DB password |
| `OLLAMA_BASE_URL`               | `http://localhost:11434` | Ollama endpoint |
| `OLLAMA_CHAT_MODEL`             | `qwen2.5:7b`           | Chat model |
| `OLLAMA_EMBEDDING_MODEL`        | `nomic-embed-text`     | Embedding model |
| `EMBEDDING_DIMENSIONS`          | `768`                  | Must match model **and** `schema.sql` vector size |
| `RETRIEVAL_TOP_K`               | `8`                    | Chunks retrieved per query |
| `RETRIEVAL_SIMILARITY_THRESHOLD`| `0.62`                 | Min cosine similarity to accept a chunk |
| `CHUNK_MAX_CHARS`               | `1000`                 | Max chunk size (chars) |
| `CHUNK_OVERLAP_CHARS`           | `150`                  | Overlap between chunks |
| `CONVERSATION_MAX_TURNS`        | `6`                    | Max history turns in prompt |
| `CONVERSATION_MAX_HISTORY_TOKENS`| `1500`                | Token budget for history |

Provider swapping is a config change, not a code change: business logic depends on the
`LlmClient` / `EmbeddingClient` interfaces, and the Spring AI provider starter + `spring.ai.*`
properties decide the actual backend.

---

## 5. Running (under 5 minutes)

```bash
# 1. Start Postgres + pgvector (if not already running)
docker run --name rag-pg -e POSTGRES_DB=ragdb -e POSTGRES_USER=raguser \
  -e POSTGRES_PASSWORD=ragpassword -p 5432:5432 -d pgvector/pgvector:pg16

# 2. Make sure Ollama is up and models are pulled
ollama pull qwen2.5:7b
ollama pull nomic-embed-text

# 3. Run the app (creates schema.sql tables on startup)
./mvnw spring-boot:run
```

Verify:
- Health: `curl http://localhost:8080/actuator/health`
- Demo UI: http://localhost:8080/index.html
- Swagger UI: http://localhost:8080/swagger-ui.html

---

## 6. API usage

All endpoints require the `X-Tenant-Id` header. `X-Correlation-Id` is optional and echoed back.

```bash
# Upload (async). Returns 202 + documentId + PROCESSING.
curl -X POST http://localhost:8080/api/v1/documents \
  -H "X-Tenant-Id: tenant-a" \
  -F "file=@fee-policy.pdf" -F "title=Fee Policy" -F "category=FEES"

# List (paginated), tenant-scoped
curl "http://localhost:8080/api/v1/documents?page=0&size=20" -H "X-Tenant-Id: tenant-a"

# Detail (watch status go PROCESSING -> READY, chunk_count > 0)
curl http://localhost:8080/api/v1/documents/{id} -H "X-Tenant-Id: tenant-a"

# Delete (document + chunks + embeddings; stops being retrievable immediately)
curl -X DELETE http://localhost:8080/api/v1/documents/{id} -H "X-Tenant-Id: tenant-a"

# Ask (non-streaming). Refuses when nothing clears the threshold.
curl -X POST http://localhost:8080/api/v1/chat \
  -H "X-Tenant-Id: tenant-a" -H "Content-Type: application/json" \
  -d '{"question":"What is the monthly fee for class VIII?","category":"FEES"}'

# Ask (streaming SSE). Tokens stream; sources arrive as a terminal event.
curl -N -X POST http://localhost:8080/api/v1/chat/stream \
  -H "X-Tenant-Id: tenant-a" -H "Content-Type: application/json" \
  -d '{"question":"And for class IX?","conversationId":"<id>"}'

# Conversation history (tenant-scoped)
curl http://localhost:8080/api/v1/conversations/{id} -H "X-Tenant-Id: tenant-a"
```

Chat response shape:

```json
{
  "conversationId": "…",
  "answer": "…",
  "grounded": true,
  "sources": [
    { "documentId": "…", "documentTitle": "Fee Policy", "pageNumber": 4,
      "similarityScore": 0.77, "snippet": "…" }
  ]
}
```

---

## 7. Architecture

Layered, with clear separation of concerns:

```
controller → service (DocumentService, ChatService, ChatStreamService, ConversationService)
           → domain (ingestion, retrieval, llm) → repository → PostgreSQL + pgvector
```

- **tenant/** — `TenantContext` (ThreadLocal), `RagRequestFilter` (binds tenant + correlation id
  into ThreadLocals/MDC per request), `CorrelationId`.
- **ingestion/** — `TextExtractor` implementations (PDF via PDFBox, DOCX via POI, TXT/MD),
  `Chunker`, `IngestionService` (async), `IngestionPersistenceService` (the single transaction).
- **retrieval/** — `RetrievalService` + `RetrievedChunk`.
- **llm/** — `LlmClient` / `EmbeddingClient` interfaces, Spring AI-backed implementations,
  `PromptFactory`, `ResiliencePolicy` (retry + circuit breaker), `CircuitBreaker`.
- **repository/** — JPA repos (documents, conversations, messages, message_sources; all
  tenant-scoped by method signature) and `ChunkRepository` (JDBC + pgvector).
- **observability/** — `RagMetrics` (Micrometer), `ModelProviderHealthIndicator`.

**Ingestion path.** `DocumentService.upload` validates type/size, computes SHA-256, persists a
`PROCESSING` row, and submits an async job on a **bounded** executor. The job extracts text
(preserving page numbers), chunks it, embeds in batches, then in **one transaction** inserts all
chunks+embeddings and flips the document to `READY`. On failure the document becomes `FAILED` with
a safe reason.

**Query path.** See the diagram in §1. Tenant + optional category filtering happen **inside the
SQL** (`ChunkRepository.search`); the similarity threshold is applied to the small top-K result
set. On no match, a fixed refusal is returned before any LLM call.

---

## 8. Chunking strategy

- **Strategy:** fixed-size character chunking with overlap, applied **within each page**.
- **Chunk size:** 1000 chars. **Overlap:** 150 chars.
- **Why:** predictable and cheap, robust across heterogeneous policy documents (tables, short
  clauses, prose). Overlap preserves context across boundaries so a fact near an edge is still
  retrievable. Chunking per page keeps page citations accurate (a chunk never spans two pages).
  Splits prefer whitespace so words aren't cut mid-token.
- **Trade-off:** fixed-size chunking does not understand table structure. Multi-column fee tables
  get flattened into a single line (see Known Limitations).

Each chunk stores: content, document id, tenant id, page number, chunk index, category, token
count, and the embedding vector.

---

## 9. Embedding model

- **Provider/model:** Ollama `nomic-embed-text`.
- **Dimensions:** 768 (the `document_chunks.embedding` column is `vector(768)`).
- **Batched:** embeddings are generated in batches (default 32), never one call per chunk.
- **Task prefixes:** `nomic-embed-text` is trained with task prefixes, so stored text is embedded
  as `search_document: …` and queries as `search_query: …`. This materially improved retrieval
  precision when multiple similar documents (e.g. two schools' fee sheets) are present. Prefixes
  are applied only to the text sent to the embedder — stored content stays raw.
- **Cost per 1000 pages:** running locally on Ollama, marginal cost is **$0** (compute/electricity
  only). The metrics layer still estimates cost from configurable per-1K-token prices so the
  pipeline is ready for a paid provider.

---

## 10. Similarity threshold

- **Configurable:** `rag.retrieval.similarity-threshold` (cosine, 0–1).
- **How it was chosen:** using real questions against the ingested fee documents, observed scores:
  - Correct-document matches (e.g. a Gurukul question against the Gurukul fee table): **0.71–0.77**.
  - Near-miss / wrong-school matches (a Gurukul question hitting the other school's fee table):
    **~0.60**.
  - Unrelated / out-of-scope chunks: **< 0.5**.
  A threshold of **~0.65** cleanly separates genuine matches from near-misses and out-of-scope
  content. The default in `application.yml` is 0.62; tighten toward 0.65 to be stricter about
  near-misses.
- These numbers are specific to `nomic-embed-text`; a different embedding model needs
  re-calibration (absolute cosine ranges differ between models).

---

## 11. Conversation memory

- Turns (user + assistant) are persisted in Postgres (`messages`, with `message_sources` citations).
- The prompt includes recent history capped by **both** a turn count (default 6 turns) **and** a
  token budget (default 1500 tokens): starting from the newest message, messages are included until
  either limit is reached. This means a few very long turns can't blow up the prompt.
- Token counts use a cheap ~4-chars-per-token estimate (documented approximation), avoiding a
  model-specific tokenizer dependency.

---

## 12. Multi-tenancy

- Tenant identity comes from the `X-Tenant-Id` header, bound to a `TenantContext` ThreadLocal for
  the request lifetime (and propagated across the async ingestion boundary).
- Every tenant-owned entity carries `tenant_id`: documents, chunks, conversations, messages.
- **Every** data-access path is tenant-scoped:
  - JPA repositories expose only tenant-scoped finders (`findByIdAndTenantId`, `findByTenantId`),
    not a bare `findById`.
  - Vector search filters `WHERE tenant_id = ?` **in the SQL** — chunks belonging to other tenants
    never leave the database (no post-filtering in Java).
- A Testcontainers integration test asserts tenant A cannot retrieve tenant B's chunks.

---

## 13. Resilience

- **Timeouts / retries:** provider calls go through `ResiliencePolicy` — bounded retries with
  exponential backoff (configurable attempts/backoff/multiplier).
- **Circuit breaker:** opens after N consecutive failures, half-opens after a cooldown, closes on
  success. While open, calls fail fast without hitting the provider.
- **Clean errors:** provider failures surface as HTTP `503` with a clean message; stack traces,
  provider internals, and secrets never reach the client (centralized `@RestControllerAdvice`).
- **Async ingestion** runs on a bounded executor with a caller-runs rejection policy (backpressure
  instead of unbounded memory growth).

---

## 14. Observability

- **Correlation IDs:** `RagRequestFilter` generates/accepts `X-Correlation-Id`, echoes it in the
  response, and pushes it into MDC. It's propagated into async ingestion via a `TaskDecorator`, so
  ingestion logs carry the originating request's correlation id.
- **Structured logs:** log lines include correlation id and tenant id. Document content, prompts,
  full LLM responses, and secrets are **not** logged — only metadata (ids, counts, durations).
- **Metrics (Micrometer, `/actuator/metrics`, `/actuator/prometheus`):** retrieval latency, model
  latency, input/output token counts, estimated cost, ingestion latency and chunk counts.
- **Health (`/actuator/health`):** database health plus a model-provider indicator driven by the
  circuit-breaker state.

---

## 15. Testing

```bash
./mvnw test
```

- **Unit tests** — `Chunker` boundary cases (empty, blank, single word, smaller/larger than a
  chunk, page boundaries) and `ResiliencePolicy` (retry, wrap-as-503, circuit opens).
- **Integration tests** — real **pgvector** via Testcontainers (not H2, not mocked DB). The model
  provider is stubbed at the `LlmClient`/`EmbeddingClient` boundary with a deterministic
  bag-of-words embedding, so **tests run with no API key / no Ollama**. Covered:
  - upload → async ingestion → `READY` with chunks persisted
  - re-upload identical content is idempotent (no duplicate chunks)
  - **tenant isolation** on retrieval
  - **refusal path** fires and does **not** call the LLM
  - grounded answer with citations + follow-up conversation
  - deletion removes chunks and stops retrieval
- A test-only `schema.sql` pins the vector column to 1536 to match the deterministic stub embedder,
  keeping tests independent of the production embedding model.

---

## 16. Performance notes

- **Retrieval < 500 ms:** query embedding + a single indexed vector query. The HNSW index
  (`vector_cosine_ops`) avoids sequential scans; tenant/category filters are indexed columns.
- **First streamed token < 3 s:** SSE streams tokens directly from the provider's reactive stream;
  no buffering of the full answer.
- **Non-blocking ingestion:** a 50-page PDF is processed on a bounded async executor, never on an
  HTTP thread. Virtual threads are enabled for request handling.
- **Streaming cancellation:** on client disconnect, the SSE emitter callbacks dispose the reactive
  subscription, cancelling the upstream model request — no orphaned calls.

---

## 17. Known limitations

Honest list of gaps and shortcuts, with what I'd do given more time.

1. **No Flyway/Liquibase.** The schema is created via `schema.sql` on startup. The assignment
   expects a migration tool; `schema.sql` is not versioned and won't handle schema evolution.
   *With more time:* reintroduce Flyway with versioned migrations (the SQL is already written).
2. **Table extraction is flat.** PDFBox extracts multi-column tables (e.g. fee sheets) as a single
   line of text. Retrieval finds the right chunk, but a small local model can misread which number
   goes with which label/column (confirmed on real fee tables). `setSortByPosition` helps reading
   order but doesn't reconstruct cells. *With more time:* add table detection (e.g. `tabula-java`)
   and emit Markdown tables before chunking so any model can read them reliably.
3. **Model comprehension.** Small local models vary a lot at reasoning over messy tabular text.
   `qwen2.5:7b` (the current default) handles fee tables noticeably better than `llama3.1:8b`;
   swapping models is a config change (`OLLAMA_CHAT_MODEL`), not a code change. A larger model or a
   hosted provider would improve accuracy further on the hardest tables.
4. **DOCX has no page model.** DOCX is ingested as a single section (page 1); PDFs preserve real
   page numbers. *With more time:* infer sections/headings for DOCX citations.
5. **No OCR.** Image-only / scanned PDFs (no text layer) are correctly rejected as "no extractable
   text" but can't be ingested. *With more time:* optional Tesseract OCR fallback.
6. **Hand-created DB columns.** During local iteration some tables were created outside `schema.sql`
   with narrower column types; the canonical types live in `schema.sql`. A clean database built from
   `schema.sql` is the source of truth.

---
