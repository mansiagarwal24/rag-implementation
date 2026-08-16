-- Test-only schema (takes precedence over src/main/resources/schema.sql on the test
-- classpath). Uses a 1536-dim vector to match the deterministic StubModels embedding,
-- keeping tests independent of whichever real embedding model production uses.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS documents (
    id              UUID PRIMARY KEY,
    tenant_id       VARCHAR(128)  NOT NULL,
    title           VARCHAR(512),
    category        VARCHAR(64),
    filename        VARCHAR(512)  NOT NULL,
    content_hash    CHAR(64)      NOT NULL,
    size_bytes      BIGINT        NOT NULL,
    status          VARCHAR(32)   NOT NULL,
    error_message   VARCHAR(2048),
    chunk_count     INT           NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_documents_tenant_hash UNIQUE (tenant_id, content_hash)
);

CREATE INDEX IF NOT EXISTS idx_documents_tenant ON documents (tenant_id);
CREATE INDEX IF NOT EXISTS idx_documents_tenant_status ON documents (tenant_id, status);

CREATE TABLE IF NOT EXISTS conversations (
    id              UUID PRIMARY KEY,
    tenant_id       VARCHAR(128)  NOT NULL,
    title           VARCHAR(512),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    last_message_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_conversations_tenant ON conversations (tenant_id);

CREATE TABLE IF NOT EXISTS messages (
    id              UUID PRIMARY KEY,
    conversation_id UUID          NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    tenant_id       VARCHAR(128)  NOT NULL,
    role            VARCHAR(16)   NOT NULL,
    content         TEXT          NOT NULL,
    token_count     INT,
    model           VARCHAR(128),
    latency_ms      BIGINT,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_messages_conversation ON messages (conversation_id, created_at);

CREATE TABLE IF NOT EXISTS document_chunks (
    id              UUID PRIMARY KEY,
    document_id     UUID          NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    tenant_id       VARCHAR(128)  NOT NULL,
    category        VARCHAR(64),
    chunk_index     INT           NOT NULL,
    content         TEXT          NOT NULL,
    page_number     INT,
    token_count     INT,
    embedding       vector(1536)  NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_chunks_tenant ON document_chunks (tenant_id);
CREATE INDEX IF NOT EXISTS idx_chunks_tenant_category ON document_chunks (tenant_id, category);
CREATE INDEX IF NOT EXISTS idx_chunks_document ON document_chunks (document_id);

CREATE INDEX IF NOT EXISTS idx_chunks_embedding_hnsw
    ON document_chunks
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

CREATE TABLE IF NOT EXISTS message_sources (
    id               UUID PRIMARY KEY,
    message_id       UUID          NOT NULL REFERENCES messages (id) ON DELETE CASCADE,
    chunk_id         UUID          REFERENCES document_chunks (id) ON DELETE SET NULL,
    document_id      UUID,
    document_title   VARCHAR(512),
    page_number      INT,
    similarity_score DOUBLE PRECISION,
    snippet          TEXT
);

CREATE INDEX IF NOT EXISTS idx_message_sources_message ON message_sources (message_id);
