---
title: Medi-Scan case study
url: /projects/medi-scan
project: Medi-Scan
owner: Saqib Choudhary
type: portfolio project (full-stack, AI)
status: built; deployment in progress (Phase 8)
last_updated: 2026-11
links:
  live: "[ADD LIVE URL]"
  repo: "[ADD GITHUB URL]"
  demo_video: "[ADD VIDEO URL]"
---

# Medi-Scan

Medi-Scan is a web app that explains medical lab reports in plain language. You upload a lab report as a PDF, a photo, or pasted text, or pick a sample report. Medi-Scan then:
- reads every value
- checks each one against the reference range printed on the report
- flags it LOW, NORMAL or HIGH
- writes a short summary at about a 6th-grade reading level

You can then ask questions about the report in a chat.

Saqib built it as a portfolio project to show end-to-end system design: an AI pipeline that is measured, safe and private, not just a wrapper around a chatbot. It runs on free tiers only. The whole project has a hard $0 cost limit.

Medi-Scan explains what a report says. It is not medical advice and does not diagnose.

---

## The problem

Lab reports are hard to read. They're full of abbreviations, units and ranges that differ between labs. People often wait days to ask a doctor what "LDL 162 mg/dL, range <100" means.

Asking a general AI chatbot has three problems:
1. **It can be confidently wrong:** it misreads a number, or decides a value is high using its own idea of "normal" instead of the range on the report.
2. **Privacy:** reports contain names, IDs, phone numbers and birth dates.
3. **Medical advice:** chatbots drift into diagnosis and treatment advice.

Medi-Scan is designed around those three problems.

---

## Features

- **Upload a report** as a text PDF, a scanned PDF, a photo (PNG or JPEG, read with OCR), or pasted text. Or try one of three synthetic sample reports.
- **Results table** with every test: value, unit, the reference range from the report, and a flag (LOW, NORMAL, HIGH, or UNKNOWN when unclear).
- **Plain-language summary** with highlights for the out-of-range values.
- **Chat about the report.** Answers stream live, use only the report and curated biomarker pages, and decline diagnosis or treatment questions.
- **Biomarker pages** that explain common tests (LDL, TSH, HbA1c and others) in plain words, with sources.
- **Guest by default:** no account needed. Guest reports are deleted after 24 hours.
- **Optional Google sign-in:** reports are kept for 30 days, with a history page and a Trends page that charts a value (for example LDL) across reports.
- **Admin dashboard:** usage totals only, with no report content or names.
- **Dark and light themes,** mobile layouts, and an accessibility pass to WCAG 2.2 AA.

---

## Tech stack

| Layer | Technology |
|---|---|
| Frontend | Next.js (App Router), TypeScript, Tailwind CSS, TanStack Query, Recharts, shadcn/ui (for dialogs and toasts only) |
| Backend | Java 21, Spring Boot 4, Spring Security 7, Spring AI, Spring Data JPA, Flyway |
| AI | Google Gemini (free tier) through Spring AI, behind a provider interface. Gemini embeddings for search |
| Database | PostgreSQL 16 with pgvector |
| Document reading | Apache PDFBox (PDF text and page rendering), Tesseract OCR (run as a separate process) |
| Privacy | Regex masking rules plus an Apache OpenNLP name model |
| Limits and caching | Bucket4j (rate limits), Caffeine (cache), an atomic daily AI-call cap in Postgres |
| Testing | JUnit, Testcontainers (real Postgres), Vitest, Testing Library, MSW, Playwright, axe |
| Hosting plan ($0) | Vercel (frontend), AWS EC2 t4g.small ARM with Docker and Caddy (backend), Neon (Postgres). Oracle Cloud Always Free as the fallback |

---

## Architecture

```text
Browser (Next.js on Vercel)
  │  HTTPS, JSON, Server-Sent Events
  ▼
Spring Boot API (Docker on an ARM server, behind Caddy)
  ├─ Upload → temp file → background job (2 workers, bounded queue)
  │    1. Extract text: PDFBox, or Tesseract OCR for photos and scanned pages
  │    2. Mask personal data (names, IDs, phones, emails, birth dates)
  │    3. AI step 1: pull out test name, value, unit, range — as printed strings only
  │    4. Code: parse numbers and ranges, compute flags, verify every value is in the text
  │    5. AI step 2: write the summary from the checked data
  │    6. Save results in one transaction
  ├─ Chat: report + checked results + retrieved biomarker pages → streamed answer
  └─ Auth: guest tokens, Google sign-in, app JWTs, rotating refresh tokens
  ▼
PostgreSQL + pgvector (Neon)
```

**How a report is processed:**
- The browser uploads the report and gets an id at once.
- It checks the report's status every 1.5 seconds until the report is done or failed.
- Only masked text ever reaches the AI or the database. Raw text stays in memory and is thrown away.

---

## Key design decisions (and why)

**1. The AI never decides numbers or flags.**
- The extraction step asks the model only for strings exactly as printed.
- Plain code parses values and ranges and computes LOW, NORMAL or HIGH.
- Every value the model returns must appear word-for-word in the report text, or it is dropped.

*Why:* a model can misread or invent a number. Code is predictable and testable.

**2. Two AI calls instead of one.**
- Step 1 extracts. Code checks the results.
- Step 2 writes the summary from the checked data only.

*Why:* the summary can't contradict the flags.

**3. Mask personal data before the AI sees anything.**
- Regex rules and a name model hide names, IDs, phones, emails and birth dates.
- An integrity check makes sure masking never changes a lab value.

*Why:* privacy, and less sensitive data leaving the server.

**4. Guest by default, Google sign-in optional.**
- Recruiters and visitors can try it without an account.
- Sign-in shows real auth work:
  - Google ID token verification
  - short-lived JWTs with key rotation by `kid`
  - rotating refresh tokens with reuse detection
  - guest reports moved to the account on sign-in

*Why:* no login wall for a demo, but a real security story.

**5. The $0 token setup.**
- No cookies. The access token lives in memory, and the refresh token in the browser tab's session storage.
- A strict Content Security Policy with per-request nonces limits the damage from a script-injection bug.

*Why:* no paid domain is needed for cookie setups.

**6. Chat uses the full report plus RAG over curated pages.**
- A lab report is short, so it goes into the prompt whole.
- pgvector search is used over reviewed biomarker pages for general questions.

*Why:* no table rows get split. Only reviewed content is used.

**7. A number guard on chat answers.**
- Answers are checked sentence by sentence before they're sent.
- If a sentence contains a number that isn't on the report, the answer stops there.

*Why:* invented "normal ranges" are the main risk in a health chat.

**8. OCR with a low-confidence rule.**
- If Tesseract wasn't sure about a value's characters, the value is shown but its flag becomes UNKNOWN, and the user is told to check it against the paper report.

*Why:* OCR can turn 5.4 into 54. A confident wrong flag is the worst possible error.

**9. Measure everything with an evaluation set.**
- About 31 synthetic reports with hand-written answer keys, plus photo and scan versions, plus about 40 chat questions.
- They measure:
  - extraction accuracy
  - flag correctness
  - "dangerous flag errors"
  - personal-data leaks
  - invented numbers
  - correct refusals

*Why:* an AI feature without numbers is just a claim.

**10. Tesseract as a separate process, not a Java library.**

*Why:* a native crash inside a library would take the whole server down. A process can be timed out and killed safely, which matters on a 2 GB server.

---

## Challenges and how they were solved

- **New library versions.** Spring Boot 4, Jackson 3 and Testcontainers 2 changed APIs and package names compared with older examples. Saqib wrote "library and version rules" into the project instructions and checked every API against current docs instead of relying on memory.
- **Truncated AI summaries.** Gemini's "thinking" used up the output budget, so summaries got cut off. Setting the thinking budget to 0 fixed truncation and cut latency.
- **Hidden retries breaking timeouts.** Some calls took 50 to 57 seconds despite a 30-second timeout, because the framework retried on top of the app's own retries. Framework retries were turned off for the evaluation, so every attempt was visible and counted against the daily cap.
- **Symbols confusing the model.** Range symbols like `≤` and `≥` were normalised to `<=` and `>=` in the summary step's input.
- **Free-tier limits.** The evaluation runs had to fit a small daily AI quota. Responses are cached on disk, so re-scoring after a code change costs no API calls.
- **Duplicated browser tabs.** "Duplicate tab" copies session storage, so two tabs shared one refresh token. That triggered reuse detection and signed the user out everywhere. Fix: a tab-id handshake over `BroadcastChannel` makes the copy drop the refresh token and sign in fresh.
- **Security vs static pages.** Nonce-based CSP makes every page render per request. Saqib kept the strict CSP on every page, including content pages, because tokens live in the browser for the whole site.
- **OCR on a small server.** Images are size-checked from the header before decoding (protection against decompression bombs). Only one OCR job runs at a time, with per-page and per-report time limits.
- **Honest UI.** The backend only reports "queued" or "processing", so the processing screen shows no fake step-by-step progress. Features not built yet are hidden behind flags instead of shown as dead buttons.

---

## Results

- **Extraction (text reports):** row recall [ADD]%, row precision [ADD]%, flag accuracy [ADD]%, dangerous flag errors [ADD].
- **OCR (photos and scans):** dangerous flag errors [ADD] (target 0), unclear-value rate [ADD]%.
- **Privacy:** personal-data leaks for masked types [ADD] (target 0). Street addresses are a known gap and are reported openly.
- **Chat:** invented numbers [ADD] (target 0), correct refusals of diagnosis and treatment questions [ADD]% (target 100%).
- **Tests:** hundreds of automated tests across backend and frontend (the client suite alone has 450+), including a "canary" test proving that a planted fake name never appears in logs, the database or AI prompts.
- **Quality:** WCAG 2.2 AA accessibility pass, Lighthouse targets of 90+ performance and 100 accessibility on the main pages [ADD final scores].
- **Cost:** $0. Free tiers only, with a daily AI-call cap and rate limits so it can't run up a bill.

---

## How it was built

Saqib designed the system himself: the plan, the data flow, the API contract, and a low-level design for each phase. He then built it phase by phase with Claude Code as the AI coding assistant, reviewing each step at set approval points. The phases were:
1. Foundation
2. Text and PDF pipeline
3. Evaluation set
4. OCR
5. Chat with RAG
6. Accounts (backend)
7. Frontend, in four parts
8. Deployment

**Git workflow:** `main` stays clean. Work happens on `dev` and short-lived feature branches.

---

## What Saqib learned

- **Measure AI work.** An evaluation set changes "it seems to work" into numbers, and catches regressions.
- **Code, not the model, for anything that must be correct.** Let the model do language. Let code do numbers and decisions.
- **Privacy has to be built in at every layer:** masking, logs, storage, token handling, and tests that prove it.
- **Auth details matter:** refresh rotation, reuse detection, and edge cases like duplicated tabs.
- **Free tiers shape the design:** caching, caps, small servers, and cold starts.

---

## Limitations and next steps

- **English only.** Masking is best effort: street addresses aren't masked yet.
- **Free-tier limits:** the AI quota is small, the database sleeps when idle, and the free AWS credits last about 6 months.
- **One server instance:** the job queue lives in memory.
- **Possible next steps:**
  - address masking
  - more biomarker pages, reviewed with sources
  - a Postgres-backed job queue for multiple instances
  - more languages

---

## Quick answers

- **What is Medi-Scan?** An app that explains lab reports in plain language, with checked values and flags, and a chat about the report.
- **Is it medical advice?** No. It explains what the report says and tells users to talk to a doctor.
- **Which AI model?** Google Gemini (free tier) through Spring AI, with the provider behind an interface so it can be swapped.
- **Where is data stored?** PostgreSQL. Only masked text and results are stored. Guest reports last 24 hours, signed-in reports 30 days. Chat messages are never stored.
- **What makes it different?**
  - code computes the flags, not the AI
  - every value is verified against the source
  - a number guard on chat
  - a low-confidence rule for OCR
  - an evaluation set with real numbers
- **Hardest part?** Making AI output trustworthy for health data: preventing confident wrong numbers in extraction, OCR and chat.
- **Is it live?** [ADD: live link, or "deployment in progress"].