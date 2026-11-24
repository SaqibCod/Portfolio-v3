---
title: Tech behind this site
url: /tech
---

# How this site is built

This portfolio is a monorepo with two halves.

**Frontend — Next.js 16 (App Router), React 19, TypeScript strict, Tailwind CSS v4.** Every
page is server-rendered at build time: the home page, the project case studies, the "Tech
behind this site" page, the guestbook and the status page. Components are server components
by default; only the interactive extras opt into the client.

**Backend — Java 25, Spring Boot 4.1, PostgreSQL 18 with pgvector, Flyway.** Spring MVC on
virtual threads, Spring Data JDBC for persistence, and a layered structure: controllers do
HTTP, services hold the rules, repositories only read and write.

## The API

Everything dynamic lives under /api/v1 and is described by an OpenAPI 3.1 contract that both
halves are built from. The endpoints:

- `POST /api/v1/chat` — this assistant. Streams server-sent events.
- `GET /api/v1/github/summary` and `/api/v1/github/activity` — contribution graph, top
  languages and recent public activity, served from a server-side cache.
- `GET /api/v1/status` and `/api/v1/status/{service}/history` — uptime and response times for
  the deployed projects.
- `GET` and `POST /api/v1/guestbook` — a guestbook where every entry is approved by hand.
- `POST /api/v1/contact` — the contact form.
- `/api/v1/admin/**` — owner-only, behind a bearer token.
- `wss://.../ws/presence` — live visitor counts.

Every error is an RFC 9457 Problem Details document with a stable `type` URI. Every public
endpoint is rate limited per hashed IP and reports its budget in `RateLimit-*` headers.

## How this chat works

It is retrieval-augmented generation over this site's own content, and nothing else.

Markdown knowledge files are chunked with Spring AI's `TokenTextSplitter`, embedded with
Google's Gemini text-embedding model at 768 dimensions, and stored in a pgvector table with
an HNSW cosine index. On every restart each file's SHA-256 hash is compared against what was
ingested last time, so unchanged content is never re-embedded.

A question is embedded, the most similar chunks are retrieved above a similarity floor, and
those chunks — plus the last few messages of the conversation — are assembled into a prompt
with strict rules: answer only from the context, say "I don't know" otherwise, never invent a
number. The answer streams back token by token over SSE, the cited pages follow as a
`sources` event, and a final `done` event names which model answered.

Gemini is the primary model. If it fails or does not produce a first token in time, the same
prompt goes to a fallback model on an OpenAI-compatible endpoint, and `done.model` reports
`fallback` so the degradation is visible rather than silent. If neither answers, the request
is a 503 and nothing is streamed.

The assistant can be wrong, and it only knows what is published on this site. For anything
else, the contact form reaches a real person.
