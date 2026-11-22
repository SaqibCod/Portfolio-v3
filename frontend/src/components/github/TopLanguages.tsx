import type { TopLanguage } from "@/lib/github";

/**
 * "48%" rather than "48.0%", but "33.3%" when the decimal carries information.
 *
 * The API rounds to one decimal place, which is the right precision to publish
 * and the wrong one to print: a trailing ".0" reads as spurious accuracy in a
 * 12px legend.
 */
function formatPercentage(percentage: number): string {
  return `${Number.isInteger(percentage) ? percentage : percentage.toFixed(1)}%`;
}

type TopLanguagesProps = {
  /** At most six languages, largest share first. */
  languages: TopLanguage[];
};

/**
 * The language breakdown: one stacked bar, then a legend naming each share.
 *
 * The bar is decorative — `aria-hidden`, because it says nothing the legend
 * below it does not say in words. Spending a screen reader's attention on a
 * second, wordless copy of the same six figures would be noise.
 *
 * Colours come from GitHub (`#b07219` for Java, and so on), so they are
 * inline styles rather than tokens: they are data, not design decisions, and
 * the set is open-ended. A language GitHub publishes no colour for falls back
 * to the muted token.
 */
export function TopLanguages({ languages }: TopLanguagesProps) {
  if (languages.length === 0) return null;

  return (
    <>
      <div
        aria-hidden="true"
        className="mt-6.5 mb-3.5 flex h-2.5 overflow-hidden rounded-full"
      >
        {languages.map((language) => (
          <i
            key={language.name}
            className="block"
            style={{
              width: `${language.percentage}%`,
              backgroundColor: language.color ?? "var(--mute)",
            }}
          />
        ))}
      </div>

      <ul role="list" className="flex list-none flex-wrap gap-x-4.5 gap-y-2 font-mono text-chip text-mute">
        {languages.map((language) => (
          <li key={language.name} className="inline-flex items-center gap-1.5">
            <i
              aria-hidden="true"
              className="inline-block size-[9px] shrink-0 rounded-full"
              style={{ backgroundColor: language.color ?? "var(--mute)" }}
            />
            {language.name} {formatPercentage(language.percentage)}
          </li>
        ))}
      </ul>
    </>
  );
}
