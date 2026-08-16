CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE documents (
    id              UUID         PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    title           VARCHAR(512) NOT NULL,
    category        VARCHAR(64),
    filename        VARCHAR(512) NOT NULL,
    content_hash    VARCHAR(64)     NOT NULL,
    size_bytes      BIGINT       NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    error_message   VARCHAR(1024),
    chunk_count     INTEGER      NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT documents_status_check
        CHECK (status IN ('PROCESSING', 'READY', 'FAILED')),
    CONSTRAINT documents_size_positive
        CHECK (size_bytes > 0),
    CONSTRAINT documents_tenant_hash_unique
        UNIQUE (tenant_id, content_hash)
);

CREATE INDEX documents_tenant_created_idx
    ON documents (tenant_id, created_at DESC);

CREATE TABLE document_chunks (
    id            UUID         PRIMARY KEY,
    document_id   UUID         NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    tenant_id     VARCHAR(64)  NOT NULL,
    chunk_index   INTEGER      NOT NULL,
    page_number   INTEGER,
    content       TEXT         NOT NULL,
    embedded_text TEXT         NOT NULL,
    category      VARCHAR(64),
    token_count   INTEGER      NOT NULL,
    embedding     vector(1536) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT document_chunks_index_unique UNIQUE (document_id, chunk_index),
    CONSTRAINT document_chunks_page_positive
        CHECK (page_number IS NULL OR page_number > 0)
);

CREATE INDEX document_chunks_tenant_category_idx
    ON document_chunks (tenant_id, category);

CREATE INDEX document_chunks_document_idx
    ON document_chunks (document_id);

CREATE INDEX document_chunks_embedding_idx
    ON document_chunks USING hnsw (embedding vector_cosine_ops);
