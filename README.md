# Portfolio

Software engineer portfolio. Static, SEO-first Next.js frontend plus a Spring Boot backend that
serves only the dynamic features (contact, guestbook, GitHub stats, status, chat, presence).

```
frontend/   Next.js 16 · React 19 · Tailwind CSS v4 · TypeScript (strict)
backend/    Java 25 · Spring Boot 4.1 · Spring Data JDBC · Flyway · PostgreSQL 18 + pgvector
```

## Prerequisites

| Tool | Version |
|---|---|
| Java | 25 (`JAVA_HOME` must point at it) |
| Node.js | 24 LTS |
| pnpm | via `corepack enable pnpm` |
| Docker | for the local database and the Testcontainers tests |

## Setup

```bash
# 1. Database
docker compose up -d
# If something already owns port 5432 (e.g. a locally installed PostgreSQL service),
# put DB_PORT=5434 in a root .env and use that port in backend/.env too.

# 2. Backend
cd backend
cp .env.example .env            # defaults already match docker-compose.yml
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
# -> http://localhost:8080/actuator/health

# 3. Frontend
cd frontend
cp .env.example .env.local
pnpm install
pnpm dev
# -> http://localhost:3000
```

## Commands

### Backend (`cd backend`)
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local   # API on :8080
./mvnw verify                                             # tests (needs Docker)
```

### Frontend (`cd frontend`)
```bash
pnpm dev          # site on :3000
pnpm build        # production build
pnpm lint         # ESLint
pnpm typecheck    # tsc --noEmit
pnpm test         # Vitest
pnpm gen:api      # regenerate src/lib/api/schema.d.ts from the OpenAPI contract
```

## Conventions

- The OpenAPI contract is the source of truth for every endpoint, field, status code and error
  shape. Frontend API types are generated from it — never hand-written.
- Backend is layered MVC with a strict direction: `controller → service → repository`.
- Schema changes go through new Flyway migrations in
  `backend/src/main/resources/db/migration/`. An applied migration is never edited.
- Errors are RFC 9457 Problem Details.
- Ignore rules live in `.git/info/exclude`; this repo has no `.gitignore`.
