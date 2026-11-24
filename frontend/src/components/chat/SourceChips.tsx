import Link from "next/link";

import type { ChatSource } from "@/lib/chat";

type SourceChipsProps = {
  sources: ChatSource[];
};

/**
 * `.src` from the home export: the pages an answer was grounded in, as small
 * mono chips below the answer itself.
 *
 * Real `<Link>`s, not buttons, so a visitor can middle-click a citation into a
 * new tab and keep reading the answer — which is most of what a citation is
 * for. The chip label is the page title with the export's trailing arrow; the
 * snippet becomes the accessible description rather than visible text, because
 * three 300-character excerpts under a 150-word answer would bury it.
 */
export function SourceChips({ sources }: SourceChipsProps) {
  if (sources.length === 0) return null;

  return (
    <ul aria-label="Sources" className="mt-2.5 flex list-none flex-wrap gap-2">
      {sources.map((source) => (
        <li key={source.url} className="min-w-0">
          <Link
            href={source.url}
            title={source.snippet}
            className="inline-flex max-w-full items-center gap-1 rounded-xs border border-line px-2 py-1.5 font-mono text-chip text-fg no-underline transition-colors duration-150 hover:border-fg focus-visible:border-fg"
          >
            <span className="truncate">{source.title}</span>
            <span aria-hidden="true">→</span>
          </Link>
        </li>
      ))}
    </ul>
  );
}
