# Document Q&A (RAG) — multi-tenant backend

Ingests a school's policy documents and answers natural-language questions about
them with citations back to document and page.

**It runs with no API key.** The default provider is a deterministic stub, so a
clean clone starts and the full test suite passes without credentials. Set
`DOCQA_AI_PROVIDER=openai` and `OPENAI_API_KEY` for real answers.

> **Status: day 2 of 10.** Skeleton, schema, container wiring, and the upload
> path. There is no extraction, retrieval, or chat endpoint yet. This README
> grows with the build; everything below is something that has actually been
> run, and the [Known limitations](#known-limitations) section is kept honest as
> of today.

## Run it

```bash
git clone <repo> && cd document-qa
docker compose up --build
curl -s localhost:8080/actuator/health
```

Expected: `{"status":"UP","components":{"db":{"status":"UP",...}}}`.

No `.env` is required. Copy `.env.example` to `.env` only when you want a keyed
provider.

## Tests

```bash
./mvnw verify
```

Integration tests use Testcontainers against a real `pgvector/pgvector:pg16`
image — never H2, which has no vector type. Docker must be running. No API key
is needed, and none is read.

## Upload path

`POST /api/v1/documents` (multipart, field `file`, optional `title` and
`category`) returns **202** with a document id and status `PROCESSING`.
Ingestion runs on a bounded executor; the request thread never does the work.

| Case | Response |
|---|---|
| Valid PDF / DOCX / TXT / MD | 202 `PROCESSING` |
| Same file uploaded again by the same tenant | 200 with the **existing** document id |
| Same file uploaded by a different tenant | 202, a distinct document |
| `.exe` renamed to `.pdf` | 415 |
| Zero-byte file | 400 |
| Over the size limit | 413 |
| Missing or malformed `X-Tenant-Id` | 400 |
| Ingestion queue saturated | 429 with `Retry-After` |

Decisions worth knowing before you extend this:

- **Type is decided by content, never by extension.** `ContentTypeSniffer` runs
  Tika's magic-byte detection with no filename hint supplied, so the client's
  chosen extension cannot influence the result. OOXML arrives as a plain zip
  under `tika-core` alone, so a DOCX is confirmed by looking for the
  `word/document.xml` entry — cheaper than carrying the whole parser stack purely
  to identify a file.
- **Idempotency is a unique constraint, not a `SELECT`.** The insert is attempted
  and `DataIntegrityViolationException` is caught. A pre-flight existence check
  loses to two identical uploads racing; the constraint does not.
- **A re-upload answers 200, not 409.** An administrator clicking twice should
  end up with one document and no error to interpret.
- **413 comes from Tomcat, not from application code.** The multipart parser
  aborts as soon as the declared size exceeds the limit, so the body is never
  buffered into heap; `ApiExceptionHandler` maps that abort to a typed 413. This
  is why `UploadSizeLimitIT` runs on a real port — MockMvc runs no multipart
  parser and would have passed regardless.
- **Queue full returns 429 with `Retry-After`,** and the document row is deleted
  rather than left in `PROCESSING`. Nothing was started, so nothing should look
  started.
- **Uploaded bytes are staged on local disk** between the 202 and the worker
  picking them up, hashed in the same pass that writes them.

## Tenancy

`X-Tenant-Id` is resolved once, at the edge, by `TenantFilter` into a
request-scoped `TenantContext`. It is not threaded through method signatures,
because a parameter can be forgotten on exactly one code path and that is always
the path that matters. A missing or malformed header is a 400 before any handler
runs. Actuator endpoints are exempt — load balancers do not send tenant headers.

**A header is acceptable for this exercise only.** Nothing validates the claim;
in production the tenant comes from a verified JWT claim. This is stated here
rather than left for the walkthrough to discover.

## Schema notes (V1)

- `UNIQUE (tenant_id, content_hash)` enforces upload idempotency **at the
  database**. The second identical upload loses the race there rather than in a
  check-then-act `SELECT` in Java.
- `ON DELETE CASCADE` from `documents` to `document_chunks`, so deleting a
  document removes everything an answer could cite it from.
- `tenant_id` is `NOT NULL` on every scoped table and leads every composite
  index, because nullable tenant columns are how leaks begin.
- The vector index is **HNSW with `vector_cosine_ops`**. IVFFlat needs
  representative rows present before `CREATE INDEX` to build useful lists, which
  a migration against an empty database cannot supply. The operator class is
  asserted in a test: a mismatch with the `<=>` query operator silently disables
  the index and every retrieval degrades to a sequential scan without erroring.
- `document_chunks` stores `content` (shown as the citation snippet) separately
  from `embedded_text` (what was sent to the embedding model, prefixed with its
  section breadcrumb), so a reader never sees synthetic heading text in a quote.
- Embedding dimension is fixed at 1536 in the DDL for
  `text-embedding-3-small`. Changing embedding model therefore requires a
  migration **and** a full re-embed of every chunk.

## Known limitations

As of day 1:

- Only the skeleton exists. Ingestion, retrieval, chat, streaming, tenancy
  enforcement, and observability are not built.
- The ingestion worker currently only logs the handoff. Extraction, chunking,
  and embedding arrive on days 3–4; `status` therefore stays `PROCESSING`
  forever today.
- `conversations`, `messages`, and `message_sources` tables are not yet created;
  they arrive in V2 alongside conversation memory.
- Staging uses local disk, which assumes a single node. With two instances
  behind a load balancer the worker may not be on the node that took the upload.
  The production answer is object storage keyed by document id.
- The orphan sweep for rows stranded in `PROCESSING` by a crash is configured
  (`orphan-timeout-minutes`) but not yet implemented.
- No rate limiting per tenant. One school can saturate the shared ingestion
  queue and make every other tenant's uploads 429.
- No OCR is planned. Image-only scanned PDFs will fail cleanly with a stored
  reason rather than silently ingesting zero chunks.
