import { BadgeLink, type BadgeTone } from "@/components/ui/Badge";

import type { components } from "@/lib/api/schema";

/** Typed from the contract so phase 07 can pass the response straight through. */
export type ServiceState = components["schemas"]["ServiceState"];

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
  /**
   * Overall health. Phase 07 supplies this from `GET /api/v1/status`; until
   * then it is undefined and the chip renders nothing rather than claiming a
   * health state nobody checked.
   */
  status?: ServiceState;
  className?: string;
};

/** The footer's status chip, linking through to the status page. */
export function StatusChip({ status, className }: StatusChipProps) {
  if (!status) return null;

  const { tone, label, live } = presentation[status];

  return (
    <BadgeLink href="/status" dot={tone} live={live} className={className}>
      {label}
    </BadgeLink>
  );
}
