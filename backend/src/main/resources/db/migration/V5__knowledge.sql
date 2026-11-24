-- The chatbot's knowledge base (phase 08).
--
-- Two tables with very different owners. `vector_store` is Spring AI's schema, written and
-- read by PgVectorStore and by nothing else in this application; `knowledge_source` is ours,
-- and exists only so that a restart can tell which files changed since the last ingestion.
--
-- Spring AI can create `vector_store` itself, but `spring.ai.vectorstore.pgvector.initialize-schema`
-- is false so that it does not: a table created at bean-initialisation time would race Flyway
-- and leave the schema in a state no migration describes. The shape below is exactly what
-- PgVectorStore writes to -- id uuid, content text, metadata json, embedding vector(n) -- so
-- the store finds what it expects and skips its own DDL.

create table vector_store (
  id uuid primary key,
  content text,
  metadata json,
  -- 768 because app.chat pins the Gemini embedding model's output dimensionality to 768.
  -- The width has to be fixed here: pgvector refuses an HNSW index above 2,000 dimensions,
  -- so a model with a wider output (gemini-embedding-001 emits 3,072 by default) needs a new
  -- migration and a full re-ingestion, not just a property change.
  embedding vector(768)
);

-- Cosine, matching `spring.ai.vectorstore.pgvector.distance-type`. The operator class and
-- the configured distance type must agree or the index is never used and every search
-- degrades to a sequential scan over the whole table.
create index vector_store_embedding_idx on vector_store using hnsw (embedding vector_cosine_ops);

-- Every chunk carries the knowledge file it came from in `metadata->>'source'`, which is how
-- re-ingesting one file deletes exactly that file's chunks. The lookup is an equality match
-- on a JSON field, so it needs its own index -- without it, editing one markdown file means a
-- sequential scan of every chunk in the table.
create index vector_store_source_idx on vector_store ((metadata ->> 'source'));

-- What was ingested, and from what. One row per knowledge file.
--
-- `content_hash` is the SHA-256 of the file's bytes, hex-encoded. Ingestion compares it to the
-- file on the classpath and does nothing when they match, which is what makes a restart free:
-- embedding is a paid API call, and the knowledge base only changes when the repository does.
--
-- There is no foreign key to `vector_store`. The link runs the other way, through the chunk
-- metadata, because one file becomes many chunks with ids Spring AI generates.
create table knowledge_source (
  path text primary key,
  content_hash char(64) not null,
  ingested_at timestamptz not null
);
