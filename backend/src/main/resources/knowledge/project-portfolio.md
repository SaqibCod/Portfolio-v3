---
title: This portfolio case study
url: /projects/portfolio
---

# This portfolio — static pages plus a small live API

The site you are reading. Two halves: a Next.js frontend renders every page that matters for
search at build time, so the content is in the HTML before any JavaScript runs, and a Spring
Boot API handles only what genuinely has to be live — this AI chat, GitHub stats, uptime
history, a moderated guestbook, the contact form and live presence.

Stack: Next.js, Spring Boot, PostgreSQL, SSE, WebSocket. Role: solo — design, backend and
frontend.

## The problem it solves

A portfolio has two jobs that pull in opposite directions. It has to be fully readable by a
crawler and fast on a cold load, and it has to show that the person who built it can do real
backend work. Making everything dynamic fails the first job; making everything static fails
the second. So the split is the point: nothing a crawler needs is ever behind a fetch, and
nothing genuinely live pretends to be static.

## How it works

Pages are pre-rendered by Next.js and served as HTML. Each live widget calls one versioned
endpoint under /api/v1 after the page has painted, so a slow or unavailable API degrades a
single card rather than the whole page. Every error is an RFC 9457 Problem Details document,
and nothing public requires an account.

## Key decisions

**Static first, dynamic only where it earns it.** Every page's text is server-rendered HTML
with its own title, description, canonical URL and JSON-LD. The interactive extras — this
chat, presence, the command palette — never hold content that exists nowhere else, so turning
JavaScript off costs you nothing but the extras.

**No accounts, no cookies, no tracking.** Nothing public requires signing in, so there are no
sessions, no CSRF handling and no consent banner. Spam is handled with Cloudflare Turnstile,
honeypot fields, per-IP rate limits and manual approval instead of identity. Visitor IP
addresses are stored only as salted SHA-256 hashes, never in the clear.

**The OpenAPI contract is the source of truth.** The frontend's API types are generated from
the same YAML the backend implements, so a renamed field fails the typecheck instead of
shipping a silently broken widget.

## Not on this site yet

[CONFIRM: Lighthouse performance score, JavaScript shipped on first load, and the number of
endpoints under /api/v1. The case study shows these as empty metric tiles.]
