import { notFound } from "next/navigation";

import { JsonLd } from "@/components/seo/JsonLd";
import { StatusOrb } from "@/components/status/StatusOrb";
import { StatusOverview } from "@/components/status/StatusOverview";
import { Container } from "@/components/ui/Container";
import { Section } from "@/components/ui/Section";
import { breadcrumbJsonLd } from "@/lib/jsonld";
import { getAllProjects } from "@/lib/projects";
import { buildMetadata } from "@/lib/seo";
import {
  fetchStatusHistory,
  fetchStatusOverview,
  SUMMARY_WINDOW,
  type StatusHistory,
} from "@/lib/status";

import type { Metadata } from "next";

const description =
  "Live uptime for everything I've deployed: current state, response times and 30 days of history, checked around the clock.";

export const metadata: Metadata = buildMetadata({
  title: "Status",
  description,
  path: "/status",
});

/**
 * How often this page may be rebuilt, in seconds.
 *
 * Written as a literal rather than as the `STATUS_REVALIDATE_SECONDS` the
 * fetches use: Next requires this export to be statically analyzable, so an
 * imported constant is not allowed here. Keep the two in step.
 */
export const revalidate = 60;

/**
 * The three cards under "How this works".
 *
 * `Every 5 minutes` repeats the backend's `app.status.interval` default. The
 * contract does not publish the interval, so there is nothing to read it from —
 * if you change that setting, change this sentence. The other two numbers come
 * from the contract itself and are checked by tests.
 */
const explainers = [
  {
    figure: "Every 5 min",
    body: "Each project's health endpoint is pinged on a schedule by this site's own API, from wherever it runs.",
  },
  {
    figure: "3 states",
    body: "Operational if it answers quickly, degraded if it answers slowly, down if it doesn't answer at all.",
  },
  {
    figure: "30 days",
    body: "History is kept for a month: 5-minute buckets over 24 hours, hourly over 7 days, daily over 30.",
  },
];

/**
 * `/status` — the uptime monitor.
 *
 * The route publishes itself from configuration. If the API reports no
 * monitored services the page does not exist: it 404s here, drops out of the
 * navigation, and stays out of the sitemap, because a status page for nothing
 * is worse than no status page. Deploying a project and adding it to the
 * backend's `app.status.services` brings all three back on its own — there is
 * nothing to change in this app.
 *
 * An unreachable API is treated as a different thing entirely, and does *not*
 * 404. "Nothing is monitored" is a statement; "the monitor is down" is the
 * absence of one, and taking a real, indexable page out of the site over a
 * transient backend outage would be the wrong trade. The design has a panel for
 * exactly that case and this renders it.
 *
 * Everything the page says is server-rendered, including each row's 30-day
 * strip — which is why the histories are fetched here rather than on mount.
 */
export default async function StatusPage() {
  const overview = await fetchStatusOverview();

  if (overview && overview.services.length === 0) {
    notFound();
  }

  const services = overview?.services ?? [];

  // In parallel: one history per service, which is what the row strips draw.
  // Sequential awaits would make a three-project page cost three round trips.
  const histories = await Promise.all(
    services.map((service) => fetchStatusHistory(service.slug, SUMMARY_WINDOW)),
  );

  const summaries: Record<string, StatusHistory | null> = {};
  services.forEach((service, index) => {
    summaries[service.slug] = histories[index];
  });

  return (
    <>
      <JsonLd
        data={breadcrumbJsonLd([
          { name: "Home", path: "/" },
          { name: "Status", path: "/status" },
        ])}
      />

      <Section spacing="none" className="overflow-hidden pt-14 md:pt-22">
        <StatusOrb state={overview?.overall ?? "unknown"} />
        <Container className="relative">
          <p className="eyebrow">Live uptime monitor</p>
          <h1 className="display metal mt-4.5 text-[clamp(56px,13vw,200px)]">
            Status
          </h1>
          <p className="mt-7 max-w-[560px] text-[17px] leading-relaxed text-mute md:text-[19px]">
            Everything I’ve deployed, checked around the clock. If something’s
            down, you’ll see it here first.
          </p>
        </Container>
      </Section>

      <StatusOverview
        initialOverview={overview}
        summaries={summaries}
        caseStudies={caseStudyPaths()}
      />

      <Section spacing="none" className="pb-30 md:pb-50">
        <Container>
          <p className="eyebrow">How this works</p>
          <ul className="mt-7 grid list-none grid-cols-1 gap-4 md:grid-cols-3">
            {explainers.map((item) => (
              <li
                key={item.figure}
                className="rounded-card border border-line bg-bg2 p-6"
              >
                <b className="display block text-[30px] leading-none">
                  {item.figure}
                </b>
                <p className="mt-3 text-[15px] leading-relaxed text-mute">
                  {item.body}
                </p>
              </li>
            ))}
          </ul>
        </Container>
      </Section>
    </>
  );
}

/**
 * Case-study links by slug.
 *
 * Derived from the MDX under `src/content/projects` rather than configured
 * anywhere: a monitored service whose slug matches a case study gets a link to
 * it, and one that does not simply has no second button. That keeps the link
 * out of the API contract, which has no business knowing this site's routes.
 */
function caseStudyPaths(): Record<string, string> {
  return Object.fromEntries(
    getAllProjects().map((project) => [project.slug, project.href]),
  );
}
