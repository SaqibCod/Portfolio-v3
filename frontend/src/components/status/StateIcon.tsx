import type { ServiceState } from "@/lib/status";

/**
 * The glyph that rides on each state's coloured disc.
 *
 * Every state has one, which is the point: a reader who cannot separate green
 * from amber still sees a tick, an exclamation, a cross or a dash. The word is
 * always beside it too — the icon is the second signal, not the only one.
 *
 * Straight from the status export, which draws the warning and unknown marks
 * differently in the banner than in a row: at 64px there is room for a full
 * triangle and question mark, at 40px there is not, so the small variants are
 * simplified rather than scaled down into mush.
 */
const paths: Record<ServiceState, { banner: React.ReactNode; row: React.ReactNode }> = {
  operational: {
    banner: <path d="M5 12l5 5 9-10" />,
    row: <path d="M5 12l5 5 9-10" />,
  },
  degraded: {
    banner: (
      <>
        <path d="M12 3L2 20h20z" />
        <path d="M12 10v4M12 17v.5" />
      </>
    ),
    row: <path d="M12 6v7M12 17v.5" />,
  },
  down: {
    banner: <path d="M6 6l12 12M18 6L6 18" />,
    row: <path d="M7 7l10 10M17 7L7 17" />,
  },
  unknown: {
    banner: <path d="M9 9a3 3 0 1 1 4 2.8c-.6.3-1 .9-1 1.6V14M12 17.5v.5" />,
    row: <path d="M7 12h10" />,
  },
};

/** The export's stroke weights, which differ per glyph rather than per size. */
const strokeWidths: Record<ServiceState, number> = {
  operational: 3,
  degraded: 2.6,
  down: 3,
  unknown: 2.6,
};

type StateIconProps = {
  state: ServiceState;
  /** `banner` is the 64px disc in the head; `row` is the 40px disc in a row. */
  variant?: "banner" | "row";
  className?: string;
};

export function StateIcon({ state, variant = "row", className }: StateIconProps) {
  const size = variant === "banner" ? 28 : 18;

  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={variant === "row" ? 3 : strokeWidths[state]}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className={className}
    >
      {paths[state][variant]}
    </svg>
  );
}
