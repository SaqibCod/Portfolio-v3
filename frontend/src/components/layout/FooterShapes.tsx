/**
 * The row of eight geometric marks that closes the footer — purely
 * decorative, so the whole row is hidden from assistive tech. Colours come
 * from the `--deco-*` tokens; `currentColor` picks up `fg`.
 */
export function FooterShapes() {
  const shapeClass =
    "size-[34px] shrink-0 md:size-18 motion-safe:animate-[wob_6s_ease-in-out_infinite]";

  return (
    <div
      aria-hidden="true"
      className="mt-10 flex justify-between gap-1 text-fg md:gap-3"
    >
      <svg viewBox="0 0 72 72" className={shapeClass}>
        <path
          d="M16 16L56 56M56 16L16 56"
          stroke="var(--deco-lime)"
          strokeWidth="16"
          strokeLinecap="round"
        />
      </svg>
      <svg viewBox="0 0 72 72" className={shapeClass}>
        <circle cx="36" cy="36" r="30" fill="var(--ac1)" />
      </svg>
      <svg viewBox="0 0 72 72" className={shapeClass}>
        <path d="M6 50A30 30 0 0 1 66 50Z" fill="currentColor" />
      </svg>
      <svg viewBox="0 0 72 72" className={shapeClass}>
        <g fill="var(--deco-orange)">
          <circle cx="24" cy="24" r="13" />
          <circle cx="48" cy="24" r="13" />
          <circle cx="24" cy="48" r="13" />
          <circle cx="48" cy="48" r="13" />
        </g>
      </svg>
      <svg viewBox="0 0 72 72" className={shapeClass}>
        <path d="M36 8L66 62H6Z" fill="var(--deco-fuchsia)" />
      </svg>
      <svg viewBox="0 0 72 72" className={shapeClass}>
        <path
          d="M8 50L22 22L36 50L50 22L64 50"
          fill="none"
          stroke="currentColor"
          strokeWidth="10"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
      <svg viewBox="0 0 72 72" className={shapeClass}>
        <circle
          cx="36"
          cy="36"
          r="24"
          fill="none"
          stroke="var(--ac1)"
          strokeWidth="12"
        />
      </svg>
      <svg viewBox="0 0 72 72" className={shapeClass}>
        <rect
          x="16"
          y="16"
          width="40"
          height="40"
          rx="10"
          fill="var(--deco-lime)"
          transform="rotate(45 36 36)"
        />
      </svg>
    </div>
  );
}
