import { TechStackList } from "@/components/projects/TechStackList";
import { JsonLd } from "@/components/seo/JsonLd";
import { Container } from "@/components/ui/Container";
import { PendingSlot } from "@/components/ui/PendingSlot";
import { Section } from "@/components/ui/Section";
import { breadcrumbJsonLd } from "@/lib/jsonld";
import { buildMetadata } from "@/lib/seo";

import type { Metadata } from "next";

const description =
  "How this site is built: a Next.js frontend that pre-renders everything search engines read, and a Spring Boot API that handles only what has to be live.";

export const metadata: Metadata = buildMetadata({
  title: "Tech behind this site",
  description,
  path: "/tech",
});

/* -------------------------------------------------------------------------- */

const halves = [
  {
    eyebrow: "Static · build time",
    title: "Next.js",
    body: "Home, case studies, project demos and this page are pre-rendered, so they load instantly and search engines read them fully.",
    stack: ["Next.js", "React", "TypeScript", "Tailwind CSS"],
  },
  {
    eyebrow: "Dynamic · runtime",
    title: "Spring Boot",
    body: "One versioned API under /api/v1 with six features. Visitors never sign in; everything public is anonymous.",
    stack: ["Java", "Spring Boot", "SSE", "WebSocket"],
  },
];

/** The four columns of the request diagram, outermost first. */
const diagram = [
  {
    stage: "Visitor",
    nodes: [{ title: "Browser", sub: "anonymous · no cookies" }],
  },
  {
    stage: "Edge",
    nodes: [
      { title: "Next.js pages", sub: "pre-rendered" },
      { title: "Turnstile", sub: "bot check" },
    ],
  },
  {
    stage: "API · /api/v1",
    nodes: [
      { title: "Chat", sub: "POST /chat · SSE" },
      { title: "GitHub", sub: "GET /github/*" },
      { title: "Status", sub: "GET /status" },
      { title: "Guestbook", sub: "GET · POST /guestbook" },
      { title: "Contact", sub: "POST /contact" },
      { title: "Presence", sub: "wss /ws/presence" },
    ],
  },
  {
    stage: "Services",
    nodes: [
      { title: "pgvector", sub: "site content embeddings" },
      { title: "Gemini", sub: "primary model" },
      { title: "Ollama", sub: "fallback model" },
      { title: "GitHub API", sub: "cached server-side" },
      { title: "Uptime checks", sub: "scheduled pings" },
      { title: "PostgreSQL", sub: "entries · history" },
      { title: "Resend", sub: "notifications" },
    ],
  },
];

const features = [
  {
    kicker: "RAG chatbot",
    title: "Ask about me",
    endpoint: "POST /api/v1/chat → text/event-stream",
    summary:
      "A chatbot that answers questions about me from this site’s own content, streaming its reply word by word.",
    points: [
      "Stateless: the browser sends the last 10 messages with each question.",
      "Streams token events, then the sources used, then a done event.",
      "If the primary model fails it switches to the fallback and says so.",
      "If both fail, a 503 is returned before the stream starts.",
    ],
  },
  {
    kicker: "Live GitHub",
    title: "Currently building",
    endpoint: "GET /api/v1/github/summary · /activity",
    summary:
      "Contribution graph, top languages and recent commits, pull requests and releases from public repositories.",
    points: [
      "Summary is cached server-side and refreshed every 6 hours.",
      "Activity is cached for 15 minutes.",
      "Only public repositories are included.",
      "If GitHub is unreachable and nothing is cached, the API returns 503 with Retry-After.",
    ],
  },
  {
    kicker: "Uptime",
    title: "Status",
    endpoint: "GET /api/v1/status · /status/{slug}/history",
    summary:
      "Scheduled checks against every deployed project, with 30 days of history.",
    points: [
      "Four states: operational, degraded, down, unknown.",
      "History in 5-minute buckets for 24h, hourly for 7d, daily for 30d.",
      "Average response time is stored alongside each bucket.",
    ],
  },
  {
    kicker: "Moderated",
    title: "Guestbook",
    endpoint: "GET · POST /api/v1/guestbook",
    summary:
      "Anyone can leave a note without an account; nothing goes public until I approve it.",
    points: [
      "Every new entry is saved as pending and emails me.",
      "Turnstile check plus a hidden honeypot field that silently drops bots.",
      "Limited to 2 entries per IP per 24 hours.",
      "Approved entries load newest first with cursor pagination.",
    ],
  },
  {
    kicker: "Messages",
    title: "Contact form",
    endpoint: "POST /api/v1/contact",
    summary:
      "Messages from the contact form are queued and delivered to my inbox.",
    points: [
      "Requires a valid Turnstile token.",
      "Honeypot field: bots get a 202 but nothing is sent.",
      "Limited to 3 messages per IP per hour.",
      "Validation errors come back per field as Problem Details.",
    ],
  },
  {
    kicker: "WebSocket",
    title: "Who’s here",
    endpoint: "wss://…/ws/presence",
    summary: "The live visitor counts you see around the site.",
    points: [
      "The browser sends join on load and on every route change.",
      "A heartbeat ping every 25 seconds keeps the connection alive.",
      "Counts are coalesced to at most one update per page per second.",
      "Nothing identifying is sent: no account, no cookie, no address.",
    ],
  },
  {
    kicker: "Build time",
    title: "Pre-rendered pages",
    endpoint: "next build",
    summary:
      "Home, case studies, project demos and this page are rendered by Next.js at build time.",
    points: [
      "Fast first load with no API call needed.",
      "Search engines get the full content.",
      "Only the live widgets on each page call the API.",
    ],
  },
];

const rules = [
  {
    title: "Versioned URLs",
    body: "Everything lives under /api/v1. Breaking changes go to /api/v2.",
  },
  {
    title: "Predictable JSON",
    body: "camelCase fields and ISO 8601 UTC timestamps everywhere.",
  },
  {
    title: "Standard errors",
    body: "RFC 9457 Problem Details, with field-level errors for validation.",
  },
  {
    title: "Honest rate limits",
    body: "RateLimit-Limit and RateLimit-Remaining on every public endpoint; 429s include Retry-After.",
  },
  {
    title: "Cursor pagination",
    body: "cursor + limit in, nextCursor out — null when you reach the end.",
  },
];

const problemJson = `{
  "type": "https://api.example.com/problems/validation",
  "title": "Validation failed",
  "status": 400,
  "errors": [
    {
      "field": "message",
      "message": "must be at most 500 characters"
    }
  ]
}`;

const privacy = [
  {
    title: "No accounts",
    body: "Visitors never sign in. Every public endpoint is anonymous.",
  },
  {
    title: "No cookies",
    body: "No cookies or sessions at all — so no CSRF handling and no consent banner.",
  },
  {
    title: "Anonymous presence",
    body: "Live counts and cursors use random per-connection ids, never your identity.",
  },
  {
    title: "Spam shields",
    body: "Turnstile, hidden honeypot fields, per-IP rate limits and my approval on every guestbook note.",
  },
];

const toolbox = [
  {
    title: "Frontend",
    items: [
      { name: "Next.js", note: "static + SSR" },
      { name: "React", note: "UI" },
      { name: "TypeScript", note: "strict" },
      { name: "Tailwind CSS", note: "design tokens" },
    ],
  },
  {
    title: "Backend",
    items: [
      { name: "Java", note: "25 LTS" },
      { name: "Spring Boot", note: "REST · SSE · WS" },
      { name: "ProblemDetail", note: "RFC 9457 errors" },
      { name: "Bucket4j", note: "rate limiting" },
    ],
  },
  {
    title: "Data & AI",
    items: [
      { name: "PostgreSQL", note: "guestbook · history" },
      { name: "pgvector", note: "RAG index" },
      { name: "Gemini", note: "chat" },
      { name: "Ollama", note: "failover" },
    ],
  },
  {
    title: "Infra",
    items: [
      { name: "[CONFIRM: frontend host]", note: "[region]" },
      { name: "[CONFIRM: API host]", note: "[region]" },
      { name: "Cloudflare Turnstile", note: "bot checks" },
      { name: "[CONFIRM: CI/CD]", note: "[pipeline]" },
    ],
  },
  {
    title: "Observability",
    items: [
      { name: "Scheduled probes", note: "every [N] min" },
      { name: "[CONFIRM: logging]", note: "[tool]" },
      { name: "Status page", note: "public" },
    ],
  },
  {
    title: "Design",
    items: [
      { name: "Archivo", note: "display + body" },
      { name: "JetBrains Mono", note: "labels · code" },
      { name: "Dark + light", note: "themes" },
    ],
  },
];

/* -------------------------------------------------------------------------- */

function BandHeading({
  eyebrow,
  children,
  metal = true,
}: {
  eyebrow: string;
  children: React.ReactNode;
  metal?: boolean;
}) {
  return (
    <>
      <p className="eyebrow">{eyebrow}</p>
      <h2
        className={`display mt-4 text-[10vw] md:text-[clamp(40px,6vw,88px)] ${metal ? "metal" : ""}`}
      >
        {children}
      </h2>
    </>
  );
}

function Tick() {
  return (
    <svg
      width="18"
      height="18"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.4"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className="mt-1 shrink-0 text-ac1"
    >
      <path d="M20 6 9 17l-5-5" />
    </svg>
  );
}

/**
 * `/tech` — the annotated version of the site.
 *
 * The export makes the architecture diagram interactive: picking a feature
 * dims every node it does not touch. Here the diagram is shown whole and each
 * feature is a card below it, so the same information is in the HTML without
 * any of it depending on client state.
 */
export default function TechPage() {
  return (
    <>
      <JsonLd
        data={breadcrumbJsonLd([
          { name: "Home", path: "/" },
          { name: "Tech behind this site", path: "/tech" },
        ])}
      />

      <Section spacing="none" className="pt-14 pb-14 md:pt-22 md:pb-18">
        <Container className="relative">
          <p className="eyebrow">Tech behind this site</p>
          <h1 className="display metal mt-4.5 max-w-[1000px] text-[10vw] md:text-[clamp(44px,8vw,120px)]">
            Under the hood
          </h1>

          <div className="mt-9 grid items-end gap-8 md:grid-cols-[minmax(0,1.3fr)_minmax(0,1fr)] md:gap-12">
            <p className="text-[18px] leading-relaxed md:text-xl">
              This site is two halves: a{" "}
              <b className="font-extrabold">Next.js</b> frontend that renders
              everything SEO-critical at build time, and a small{" "}
              <b className="font-extrabold">Spring Boot</b> API that only
              handles what has to be live — the AI chat, GitHub stats, uptime,
              the guestbook, contact and presence.
            </p>
            <PendingSlot label="Right now">
              People on the site, API status and the last GitHub sync land here
              once presence, status and the GitHub integration are wired up.
            </PendingSlot>
          </div>

          <div className="mt-14 grid gap-4 md:grid-cols-2">
            {halves.map((half) => (
              <div
                key={half.title}
                className="rounded-lg border border-line bg-bg2 p-5 md:rounded-xl md:p-7"
              >
                <p className="eyebrow">{half.eyebrow}</p>
                <h2 className="display mt-2.5 text-2xl md:text-3xl">
                  {half.title}
                </h2>
                <p className="mt-2.5 text-base leading-relaxed text-mute">
                  {half.body}
                </p>
                <TechStackList
                  items={half.stack}
                  label={`${half.title} stack`}
                  className="mt-4.5"
                />
              </div>
            ))}
          </div>
        </Container>
      </Section>

      {/* How a request flows */}
      <Section id="flow" inverted rounded className="mt-20 md:mt-30">
        <Container>
          <div className="mb-9 flex flex-col items-start gap-4 md:flex-row md:items-end md:justify-between md:gap-6">
            <div>
              <p className="eyebrow">How a request flows</p>
              <h2 className="display mt-4 text-[10vw] md:text-[clamp(40px,6vw,88px)]">
                Architecture
              </h2>
            </div>
            <p className="max-w-[380px] leading-relaxed text-mute">
              Four stages, from the browser to the services behind the API.
              Every feature below touches a different slice of it.
            </p>
          </div>

          <div
            role="img"
            aria-label="Four stages. The visitor's browser loads pre-rendered Next.js pages and passes a Turnstile bot check at the edge. Those call the Spring Boot API under /api/v1, which exposes chat, GitHub, status, guestbook, contact and presence endpoints. The API in turn reads from pgvector, the Gemini and Ollama models, the GitHub API, scheduled uptime checks, PostgreSQL and Resend."
            className="grid gap-4 rounded-xl border border-line bg-bg2 p-5 md:grid-cols-4 md:gap-5 md:p-6"
          >
            {diagram.map((column) => (
              <div key={column.stage} className="flex min-w-0 flex-col gap-2.5">
                <p className="eyebrow text-[11px]">{column.stage}</p>
                {column.nodes.map((node) => (
                  <div
                    key={node.title}
                    className="rounded-input border border-line bg-inv p-3.5"
                  >
                    <b className="block text-sm font-bold" translate="no">
                      {node.title}
                    </b>
                    <span className="mt-1 block font-mono text-[11px] text-mute">
                      {node.sub}
                    </span>
                  </div>
                ))}
              </div>
            ))}
          </div>

          <ul
            aria-label="Features and the endpoints behind them"
            className="mt-5 grid list-none gap-4 md:grid-cols-2"
          >
            {features.map((feature) => (
              <li
                key={feature.title}
                className="flex flex-col rounded-lg border border-line bg-bg2 p-5 md:rounded-card md:p-7"
              >
                <p className="eyebrow">{feature.kicker}</p>
                <h3 className="display mt-3 text-[clamp(24px,3vw,32px)]">
                  {feature.title}
                </h3>
                <code
                  className="mt-3.5 inline-block self-start rounded-xs border border-line px-2.5 py-2 font-mono text-eyebrow break-all"
                  translate="no"
                >
                  {feature.endpoint}
                </code>
                <p className="mt-4 leading-relaxed text-mute">
                  {feature.summary}
                </p>
                <ul className="mt-4.5 flex list-none flex-col gap-3">
                  {feature.points.map((point) => (
                    <li key={point} className="flex gap-3 text-[15px] leading-normal">
                      <Tick />
                      <span>{point}</span>
                    </li>
                  ))}
                </ul>
              </li>
            ))}
          </ul>
        </Container>
      </Section>

      {/* API conventions */}
      <Section rounded className="-mt-11 bg-bg">
        <Container>
          <BandHeading eyebrow="API conventions">House rules</BandHeading>
          <div className="mt-10 grid items-start gap-10 md:grid-cols-2 md:gap-12">
            <ol className="list-none">
              {rules.map((rule, index) => (
                <li
                  key={rule.title}
                  className="grid grid-cols-[44px_minmax(0,1fr)] gap-4 border-t border-line py-5 last:border-b"
                >
                  <span
                    aria-hidden="true"
                    className="display text-[28px] leading-none text-transparent [-webkit-text-stroke:1.5px_var(--fg)]"
                  >
                    {String(index + 1).padStart(2, "0")}
                  </span>
                  <div>
                    <b className="block text-[17px]">{rule.title}</b>
                    <span className="mt-1 block text-[15px] leading-normal text-mute">
                      {rule.body}
                    </span>
                  </div>
                </li>
              ))}
            </ol>
            <div className="overflow-hidden rounded-lg border border-line bg-code">
              <div className="border-b border-inv-line px-4.5 py-3">
                <span className="font-mono text-chip text-zinc-400" translate="no">
                  400 · application/problem+json
                </span>
              </div>
              <pre className="overflow-x-auto px-4.5 py-5 font-mono text-[13px] leading-[1.75] text-zinc-200">
                <code translate="no">{problemJson}</code>
              </pre>
            </div>
          </div>
        </Container>
      </Section>

      {/* Privacy */}
      <Section spacing="none" className="py-20">
        <Container>
          <BandHeading eyebrow="Privacy & security">No tracking</BandHeading>
          <ul
            aria-label="Privacy and security measures"
            className="mt-8 grid list-none gap-3.5 md:grid-cols-4"
          >
            {privacy.map((item) => (
              <li
                key={item.title}
                className="flex flex-col gap-3 rounded-lg border border-line bg-bg2 p-5"
              >
                <span
                  aria-hidden="true"
                  className="flex size-11 items-center justify-center rounded-md border border-line text-ac1"
                >
                  <svg
                    width="20"
                    height="20"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    aria-hidden="true"
                  >
                    <path d="M12 3l7 3v6c0 4.2-2.9 7.7-7 9-4.1-1.3-7-4.8-7-9V6z" />
                  </svg>
                </span>
                <b className="text-[17px]">{item.title}</b>
                <p className="text-sm leading-relaxed text-mute">{item.body}</p>
              </li>
            ))}
          </ul>
        </Container>
      </Section>

      {/* The toolbox */}
      <Section spacing="none" className="pt-10 pb-35 md:pb-50">
        <Container>
          <BandHeading eyebrow="Full stack">The toolbox</BandHeading>
          <div className="mt-8 grid gap-3.5 md:grid-cols-3">
            {toolbox.map((group) => (
              <section
                key={group.title}
                className="rounded-lg border border-line bg-bg2 p-5.5"
              >
                <h3 className="text-base font-extrabold uppercase [font-stretch:115%]">
                  {group.title}
                </h3>
                <dl className="mt-3.5">
                  {group.items.map((item) => (
                    <div
                      key={item.name}
                      className="flex justify-between gap-3 border-t border-line py-2.5 text-[15px]"
                    >
                      <dt className="min-w-0" translate="no">
                        {item.name}
                      </dt>
                      <dd className="shrink-0 text-right font-mono text-chip text-mute">
                        {item.note}
                      </dd>
                    </div>
                  ))}
                </dl>
              </section>
            ))}
          </div>
        </Container>
      </Section>
    </>
  );
}
