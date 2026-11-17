/**
 * Stagger from the export's `:nth-child(2n)` / `:nth-child(3n)` rules, where
 * `3n` wins on the sixth mark because it comes later in the stylesheet.
 * Applied per shape so the result doesn't depend on utility output order.
 */
const WOBBLE_DELAYS = ["0s", "-1.5s", "-3s", "-1.5s", "0s", "-3s", "0s", "-1.5s"];

/**
 * `wob` animates the standalone `rotate` property while the hover rule sets
 * `transform`, so the two compose instead of fighting. Writing the hover as
 * `rotate-*`/`scale-*` would lose to the running animation.
 */
const shapeClass =
  "size-[34px] shrink-0 transition-transform duration-400 " +
  "ease-[cubic-bezier(.3,1.6,.5,1)] " +
  "hover:[transform:rotate(24deg)_scale(1.15)] " +
  "motion-safe:animate-[wob_6s_ease-in-out_infinite] md:size-18";

function Shape({
  index,
  children,
}: {
  index: number;
  children: React.ReactNode;
}) {
  return (
    <svg
      viewBox="0 0 72 72"
      className={shapeClass}
      style={{ animationDelay: WOBBLE_DELAYS[index] }}
    >
      {children}
    </svg>
  );
}

/**
 * The row of eight geometric marks that closes the footer — purely
 * decorative, so the whole row is hidden from assistive tech. Colours come
 * from the `--deco-*` tokens; `currentColor` picks up `fg`.
 */
export function FooterShapes() {
  return (
    <div
      aria-hidden="true"
      className="mt-10 flex justify-between gap-1 text-fg md:gap-3"
    >
      <Shape index={0}>
        <path
          d="M16 16L56 56M56 16L16 56"
          stroke="var(--deco-lime)"
          strokeWidth="16"
          strokeLinecap="round"
        />
      </Shape>
      <Shape index={1}>
        <circle cx="36" cy="36" r="30" fill="var(--ac1)" />
      </Shape>
      <Shape index={2}>
        <path d="M6 50A30 30 0 0 1 66 50Z" fill="currentColor" />
      </Shape>
      <Shape index={3}>
        <g fill="var(--deco-orange)">
          <circle cx="24" cy="24" r="13" />
          <circle cx="48" cy="24" r="13" />
          <circle cx="24" cy="48" r="13" />
          <circle cx="48" cy="48" r="13" />
        </g>
      </Shape>
      <Shape index={4}>
        <path d="M36 8L66 62H6Z" fill="var(--deco-fuchsia)" />
      </Shape>
      <Shape index={5}>
        <path
          d="M8 50L22 22L36 50L50 22L64 50"
          fill="none"
          stroke="currentColor"
          strokeWidth="10"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </Shape>
      <Shape index={6}>
        <circle
          cx="36"
          cy="36"
          r="24"
          fill="none"
          stroke="var(--ac1)"
          strokeWidth="12"
        />
      </Shape>
      <Shape index={7}>
        <rect
          x="16"
          y="16"
          width="40"
          height="40"
          rx="10"
          fill="var(--deco-lime)"
          transform="rotate(45 36 36)"
        />
      </Shape>
    </div>
  );
}
