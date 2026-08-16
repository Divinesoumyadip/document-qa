# Document Q&A (RAG) — multi-tenant backend

Ingests a school's policy documents and answers natural-language questions about
them, with citations back to document and page.

**It runs with no API key.** The default provider is a deterministic local stub,
so a clean clone starts and the full test suite passes without credentials. Set
`DOCQA_AI_PROVIDER=openai` and `OPENAI_API_KEY` for real answers.

## Run it in under five minutes

```bash
git clone https://github.com/Divinesoumyadip/document-qa.git
cd document-qa
docker compose up --build
```

Then:

```bash
curl -s localhost:8080/actuator/health

# Upload
curl -X POST localhost:8080/api/v1/documents \
  -H 'X-Tenant-Id: school-a' \
  -F 'file=@fee-policy.pdf' \
  -F 'title=Fee Policy 2024' \
  -F 'category=FEES'

# Ask a grounded question
curl -X POST localhost:8080/api/v1/chat \
  -H 'X-Tenant-Id: school-a' \
  -H 'Content-Type: application/json' \
  -d '{"question":"What is the late fee for term 2?"}'

# Out of scope — refusal, no LLM call
curl -X POST localhost:8080/api/v1/chat \
  -H 'X-Tenant-Id: school-a' \
  -H 'Content-Type: application/json' \
  -d '{"question":"Who won the 2018 World Cup?"}'
```

Tests:

```bash
./mvnw verify     # requires Docker; no API key needed
```

### If Testcontainers cannot reach Docker

Docker Engine 29 rejects the API version bundled with Testcontainers 1.20. On
such hosts, allow the older client:

```bash
sudo mkdir -p /etc/systemd/system/docker.service.d
printf '[Service]\nEnvironment=DOCKER_MIN_API_VERSION=1.24\n' | \
  sudo tee /etc/systemd/system/docker.service.d/api-version.conf
sudo systemctl daemon-reload && sudo systemctl restart docker
```

## Architecture

**Ingestion path** — async, off the request thread:

```
POST /api/v1/documents
  → sniff content type from magic bytes (never the extension)
  → stream to staging dir, SHA-256 in the same pass
  → INSERT documents (PROCESSING)   ← unique (tenant_id, content_hash)
  → 202 Accepted returned here
  → [bounded executor]
      → extract text with page numbers (PDFBox / POI / plain)
      → recursive chunking, 700 chars, 100 overlap
      → batched embeddings
      → batch INSERT chunks + vectors  ─┐ one transaction
      → status READY or FAILED         ─┘
```

**Query path:**

```
POST /api/v1/chat
  → embed question
  → vector search: tenant + category filtered IN SQL, ORDER BY <=>, LIMIT k
  → drop anything below the similarity threshold
  → if nothing remains: fixed refusal, LLM is never called
  → else: build prompt from chunks, call model, return answer + sources
```

## Chunking strategy and why

**Recursive character splitting, 700 characters, 100 overlap.**

The corpus is short structured policies — headings, numbered clauses, small fee
tables. Fixed-size splitting cuts blindly at N characters, which is how "the late
fee is Rs 500" gets separated from "for Term 2". Recursive splitting tries
separators in priority order (paragraph → line → sentence → word → char) and only
descends when a piece still exceeds the target, so a whole clause stays intact
whenever it fits.

700 characters is roughly 175 tokens — large enough to hold a complete policy
clause with its heading, small enough that five retrieved chunks leave room for
conversation history in the prompt budget. The 100-character overlap means a fact
spanning a boundary appears whole in at least one chunk.

**What gets embedded is not what gets shown.** Each chunk stores `content` (raw
text, used as the citation snippet) separately from `embedded_text` (the same
text prefixed with `Title [page N, chunk M]`). The breadcrumb helps retrieval on
questions like "what is the fee" where the chunk body never repeats the
document's subject — but a parent reading a citation should never see synthetic
heading text inside a quoted policy.

## Embedding model, dimensions, cost

| | |
|---|---|
| Model | `text-embedding-3-small` |
| Dimensions | 1536 (fixed in the DDL) |
| Batch size | 512 inputs per API call |
| Cost | $0.02 per million tokens |

**Cost per 1000 pages:** a policy page is ~500 words ≈ 650 tokens. 1000 pages ≈
650,000 tokens ≈ **$0.013 per full re-index**. Re-embedding the whole corpus
costs about a cent, which is why chunk-size experiments are cheap. The dimension
being fixed in the schema is the real constraint — switching embedding model
requires a migration *and* a full re-embed.

## Similarity threshold

**Production default: 0.65. Test profile: 0.25.**

Honest status: 0.65 is a starting value, not yet calibrated against a real corpus
with real embeddings. That calibration is the highest-priority remaining work.

The test profile carries its own threshold because the stub embedder is
bag-of-words — shared vocabulary produces cosine similarity on a different scale
than a semantic model. A 4-word question against a 20-word chunk tops out near
0.45 even with perfect word overlap, because the chunk's vector spreads across
more buckets. One hardcoded number for both would mean either the tests retrieve
nothing or production accepts near-misses.

What the tests verify is that the **threshold mechanism** works: chunks above it
are returned, chunks below trigger refusal.

## Multi-tenancy

`X-Tenant-Id` is resolved once at the edge by `TenantFilter` into a
request-scoped `TenantContext`. It is not threaded through method signatures,
because a parameter can be forgotten on exactly one code path and that is always
the path that matters.

Enforcement is layered:

- `tenant_id` is `NOT NULL` on every scoped table and leads every composite index
- every repository finder takes a tenant id
- retrieval filters by tenant **inside the SQL**, before the vector ordering
- cross-tenant access returns **404, not 403** — 403 confirms the id exists,
  which is itself a leak

`ChatServiceIT.tenantCannotRetrieveAnotherTenantsChunks` ingests as one tenant
and asks as another, asserting refusal.

**A header is acceptable for this exercise only.** Nothing validates the claim;
in production the tenant comes from a verified JWT claim.

## Notable decisions

**Idempotency is a unique constraint, not a `SELECT`.** The insert is attempted
and `DataIntegrityViolationException` caught. A pre-flight check loses to two
identical uploads racing; the constraint does not. A re-upload returns 200 with
the existing document — an administrator clicking twice should end up with one
document, not an error.

**`ChunkWriter` bypasses JPA.** Hibernate maps `float[]` to bytea and String to
varchar; Postgres accepts neither for a `vector` column. The insert uses a
batched `JdbcTemplate` with `CAST(? AS vector)` — also the right answer for bulk
vector writes regardless.

**HNSW, not IVFFlat.** IVFFlat needs representative rows present before
`CREATE INDEX` to build useful lists, which a migration against an empty database
cannot supply. The operator class is asserted in a test: an index built with the
wrong ops class is silently ignored by the `<=>` query and every retrieval
degrades to a sequential scan without erroring.

**413 comes from Tomcat, not application code.** The multipart parser aborts as
soon as the declared size exceeds the limit, so the body never reaches heap.
`UploadSizeLimitIT` runs on a real port because MockMvc runs no multipart parser
and would pass regardless.

**Bounded executor, not virtual threads, for the ingestion queue.** Virtual
threads give unlimited concurrency — right for IO inside a job, wrong for the
queue itself, where backpressure is the feature. `AbortPolicy` is how the caller
learns to answer 429 instead of accepting work it will never do.

**Streaming cancels three ways.** Client disconnect, timeout, and an `IOException`
on send all flip an `AtomicBoolean` the token consumer checks, unwinding the
provider call. Sources are a distinct terminal SSE event, never inline with
tokens.

## Testing

All green with no API key set.

- Chunker boundary cases: empty, blank, single word, exceeds one chunk
- Content sniffing: executable renamed to `.pdf`, plain zip renamed to `.docx`
- Testcontainers against real `pgvector/pgvector:pg16` — never H2, which has no
  vector type and would prove nothing about the query that matters
- Tenant isolation on retrieval
- Refusal path fires when nothing clears the threshold
- Deletion removes chunks; answers stop citing immediately
- Circuit breaker opens, retries exhaust, no provider detail reaches the client

## Known limitations

- **The threshold is not calibrated** against a real corpus with real embeddings.
- **Conversation history is built but not wired into the prompt.**
  `ConversationHistoryService` applies the token budget and
  `GET /conversations/{id}` works, but `ChatService` does not prepend history, so
  follow-ups like "what about class 9?" will not resolve.
- **Metrics are recorded but not called from the hot paths.** `AiMetrics` exists
  with token and cost counters; wiring it into `RetrievalService` and the chat
  clients remains.
- **Staging uses local disk** — a single-node assumption. With two instances
  behind a load balancer the worker may not be on the node that took the upload.
  The production answer is object storage keyed by document id.
- **No orphan sweep.** Rows stranded in `PROCESSING` by a crash stay there;
  `orphan-timeout-minutes` is configured but unused.
- **No OCR.** Image-only scanned PDFs fail cleanly with a stored reason rather
  than silently ingesting zero chunks.
- **No per-tenant rate limiting.** One school can saturate the shared ingestion
  queue and make every other tenant's uploads 429.
- **No hybrid search or re-ranking** — stretch goals, deliberately not started
  while Tier 1–3 items remain.

## With two more weeks

1. Calibrate the threshold properly — build the golden question set (direct
   facts, multi-hop, follow-ups, near-misses, out-of-scope) and sweep thresholds
   against it, recording where out-of-scope answers start leaking through.
2. Wire conversation history into the prompt, with follow-up tests.
3. Hybrid search: fuse vector similarity with Postgres full-text search. Policy
   questions often hinge on exact terms ("Term 2", "Rs 500") where lexical match
   beats semantic similarity.
4. Move staging to object storage so the service scales past one node.
5. An evaluation harness in CI, so a chunking change that degrades retrieval
   fails the build instead of surfacing in a demo.

## One thing that surprised me

How much of the difficulty was infrastructure rather than RAG. The retrieval
logic is maybe 80 lines. Getting there meant discovering that Boot 4 splits
Flyway's autoconfiguration into a separate starter — without it, migrations
silently never run and Hibernate reports a missing table — that Testcontainers
1.20 speaks a Docker API version Engine 29 rejects outright, and that JPA has no
path to a pgvector column at all.

The second surprise was the stub embedder. The first version hashed text to a
pseudorandom vector: deterministic, fast, and useless, because two strings
sharing every word still scored near-zero similarity. A suite built on it would
have passed while proving nothing about retrieval. Making the stub bag-of-words
was the difference between a test that runs and a test that means something.
