---
title: githMind case study
url: /projects/githmind
---

# githMind — chat with any GitHub repository

githMind answers questions about a GitHub repository in plain language: what a module does,
why a file changed, where a behaviour is implemented. You point it at a repository, it indexes
the code and the commit history once, and from then on every answer cites the files it came
from.

Stack: Next.js, Spring Boot, RAG, PostgreSQL, pgvector. Kind: AI and full-stack.

## The problem it solves

Reading an unfamiliar codebase is mostly navigation, not comprehension. You open a file,
follow a call three layers down, lose the thread, and start again. Text search finds the
string but not the reason, and the commit that explains it is six hundred commits back.

## How it works

A question typed in the browser goes to the Spring Boot API, which embeds it, retrieves the
most similar code and commit chunks from the vector index, builds a prompt around them, and
streams the model's reply back token by token. The sources arrive after the answer, so the
citation list is only ever built from chunks that were actually in the prompt.

The index itself is built by an ingestion step that reads repository files and history from
the GitHub API, then chunks and embeds them into pgvector.

## Key decisions

**Streaming answers over SSE.** A grounded answer takes seconds to assemble, and a spinner
for that long reads as broken. Server-sent events let the first tokens land almost
immediately, with the citation list following once the answer is complete.

## Not on this site yet

[CONFIRM: how code is chunked — by symbol, by file, or by sliding window — and what the
rejected alternative cost in retrieval quality.]

[CONFIRM: the free-tier constraint and the trade-off it forced.]

[CONFIRM: repositories indexed, median time to first token, and the share of answers returned
with citations. The case study shows these as empty metric tiles, so there are no numbers to
quote.]

[CONFIRM: live demo URL and public repository URL. Neither is published yet.]

[CONFIRM: the hardest bug, and the one lesson carried into the next project.]
