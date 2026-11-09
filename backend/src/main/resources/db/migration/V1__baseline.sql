-- Baseline: extensions every later migration relies on.
-- No tables yet; feature schemas arrive with the phases that need them.

-- pgvector, for the chatbot's embedding store (phase 08).
CREATE EXTENSION IF NOT EXISTS vector;

-- pgcrypto, for gen_random_uuid() as a column default.
CREATE EXTENSION IF NOT EXISTS pgcrypto;
