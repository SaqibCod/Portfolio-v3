---
title: Medi-Scan case study
url: /projects/medi-scan
---

# Medi-Scan — plain-language lab reports

Medi-Scan turns a lab report into something you can actually read. Every value is explained in
ordinary language, with the reference range it is measured against and a citation back to the
source the explanation came from. It runs on a zero-cost stack and falls back to a second
model automatically when the primary one is unavailable.

Stack: Next.js, Spring AI, pgvector, Gemini and Ollama, Resilience4j. Kind: AI and full-stack.

## The problem it solves

A lab report is a table of numbers written for someone who already knows what they mean. The
patient holding it gets a column of values, a reference range in small print, and no
explanation — so the next step is usually a search engine and an afternoon of worry.

## How it works

An uploaded report is parsed into individual values. Each value is matched against retrieved
reference material in the vector store, and the model is asked to explain only what it was
given. If the primary model fails or times out, a circuit breaker switches to the local
fallback, and the response says which model answered.

## Key decisions

**A second model instead of a retry.** Retrying a rate-limited model just fails more slowly.
A circuit breaker in front of a local fallback keeps the feature answering, and the response
names the model so the degradation is visible rather than silent.

## Not on this site yet

[CONFIRM: how reports are parsed, and what happens with a format the parser has never seen.]

[CONFIRM: what the product refuses to do — no diagnosis, no advice, no stored reports — and
how the interface makes that clear.]

[CONFIRM: reports analysed, median analysis time, and the share of values matched to a
reference range. The case study shows these as empty metric tiles.]

[CONFIRM: live demo URL and public repository URL.]
