"use client";

import { useEffect, useState } from "react";

import { BadgeLink, type BadgeTone } from "@/components/ui/Badge";
import { fetchStatusOverview, type ServiceState } from "@/lib/status";

/** Re-exported for the components that were typed against this before phase 07. */
export type { ServiceState };

/**
 * Only "All systems operational" appears in the exports; the other three
 * states reuse the same chip with the matching dot tone. The dot pulses only
 * while everything is healthy, as in the export.
 */
const presentation: Record<ServiceState, { tone: BadgeTone; label: string; live: boolean }> = {
  operational: { tone: "ok", label: "All systems operational", live: true },
  degraded: { tone: "warn", label: "Degraded performance", live: false },
  down: { tone: "err", label: "Service disruption", live: false },
  unknown: { tone: "off", label: "Status unknown", live: false },
};

type StatusChipProps = {
  className?: string;
};

/**
 * The footer's status chip, linking through to the status page.
 *
 * Fetched in the browser rather than on the server, which is a deliberate
 * trade. The footer is on every page, so a server fetch here would put the
 * whole site — case studies, the tech page, the project index — on a
 * one-minute revalidation cycle for the sake of one chip. Fetching on mount
 * keeps those pages static and keeps the chip honest; a stale "all systems
 * operational" during an outage would be worse than no chip at all.
 *
 * Nothing is rendered until an answer arrives, and nothing is rendered if the
 * answer is that no project is monitored or that the API is unreachable. So the
 * chip appears and disappears with the status page itself, from the backend's
 * configuration, with nothing to change here.
 *
 * This is safe to leave out of the HTML because it holds no information of its
 * own: it is a summary of `/status`, which is a page in the sitemap and is
 * linked from the footer's Site column in full.
 */
export function StatusChip({ className }: StatusChipProps) {
  const [state, setState] = useState<ServiceState | null>(null);

  useEffect(() => {
    let active = true;

    fetchStatusOverview().then((overview) => {
      if (!active || !overview || overview.services.length === 0) return;
      setState(overview.overall);
    });

    return () => {
      active = false;
    };
  }, []);

  if (!state) return null;

  const { tone, label, live } = presentation[state];

  return (
    <BadgeLink href="/status" dot={tone} live={live} className={className}>
      {label}
    </BadgeLink>
  );
}
