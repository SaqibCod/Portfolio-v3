/**
 * Jumps keyboard and screen-reader users past the header. Hidden until
 * focused, then rendered as the design's gradient pill above the sticky nav.
 *
 * The exports have no skip link, so this borrows their button language
 * (999px pill, uppercase label, violet focus ring).
 */
export function SkipLink() {
  return (
    <a
      href="#main"
      className={[
        "sr-only",
        "focus-visible:not-sr-only focus-visible:fixed focus-visible:top-4 focus-visible:left-4 focus-visible:z-50",
        "focus-visible:inline-flex focus-visible:min-h-11 focus-visible:items-center",
        "focus-visible:grad focus-visible:rounded-full focus-visible:px-[22px]",
        "focus-visible:text-btn focus-visible:font-semibold focus-visible:tracking-[0.04em]",
        "focus-visible:text-white focus-visible:uppercase focus-visible:no-underline",
      ].join(" ")}
    >
      Skip to content
    </a>
  );
}
