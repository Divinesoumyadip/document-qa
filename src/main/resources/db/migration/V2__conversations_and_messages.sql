CREATE TABLE conversations (
    id              UUID         PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    title           VARCHAR(512),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_message_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX conversations_tenant_idx ON conversations (tenant_id, last_message_at DESC);

CREATE TABLE messages (
    id              UUID         PRIMARY KEY,
    conversation_id UUID         NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    role            VARCHAR(16)  NOT NULL,
    content         TEXT         NOT NULL,
    token_count     INTEGER,
    model           VARCHAR(64),
    latency_ms      INTEGER,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT messages_role_check CHECK (role IN ('user', 'assistant', 'system'))
);

CREATE INDEX messages_conversation_idx ON messages (conversation_id, created_at ASC);

CREATE TABLE message_sources (
    id              UUID         PRIMARY KEY,
    message_id      UUID         NOT NULL REFERENCES messages (id) ON DELETE CASCADE,
    chunk_id        UUID         NOT NULL REFERENCES document_chunks (id) ON DELETE CASCADE,
    similarity_score DOUBLE PRECISION NOT NULL
);
