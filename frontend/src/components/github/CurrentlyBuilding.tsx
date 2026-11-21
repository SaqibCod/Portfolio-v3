import { formatCompactRelativeTime } from "@/lib/format";

import type { GithubActivityItem } from "@/lib/github";

/** What each kind of item is called in the row's second line. */
const typeLabels: Record<GithubActivityItem["type"], string> = {
  commit: "commit",
  pull_request: "pull request",
  release: "release",
};

/**
 * The export's three row icons, which are the only thing distinguishing the
 * kinds at a glance. Decorative: the kind is also written out beside the
 * repository name, so each is `aria-hidden` rather than labelled twice.
 */
function ActivityIcon({ type }: { type: GithubActivityItem["type"] }) {
  const shared = {
    width: 16,
    height: 16,
    viewBox: "0 0 24 24",
    fill: "none",
    stroke: "currentColor",
    strokeWidth: 2,
    "aria-hidden": true,
  } as const;

  if (type === "commit") {
    return (
      <svg {...shared}>
        <circle cx="12" cy="12" r="4" />
        <path d="M2 12h6M16 12h6" />
      </svg>
    );
  }
  if (type === "pull_request") {
    return (
      <svg {...shared} strokeLinecap="round">
        <circle cx="6" cy="6" r="2.5" />
        <circle cx="6" cy="18" r="2.5" />
        <circle cx="18" cy="18" r="2.5" />
        <path d="M6 8.5v7M18 15.5V9a3 3 0 0 0-3-3h-4M13 4l-2 2 2 2" />
      </svg>
    );
  }
  return (
    <svg {...shared} strokeLinejoin="round">
      <path d="M3 12V4h8l10 10-8 8z" />
      <circle cx="7.5" cy="8" r="1.5" />
    </svg>
  );
}

type CurrentlyBuildingProps = {
  /** Newest first, as the API returns them. */
  items: GithubActivityItem[];
  /**
   * When the figures were read from GitHub, for the "updated 2h ago" label.
   * Omitted when there is nothing to date.
   */
  updatedAt?: string;
};

/**
 * The "Recent activity" list: what was last committed, opened or released.
 *
 * A server component. Every row, including its relative time, is in the HTML —
 * the time is computed once here and never recomputed in the browser, which is
 * what makes a relative string safe to server-render (see
 * {@link formatCompactRelativeTime}).
 *
 * Renders nothing when the list is empty. The caller decides what an absent
 * list means, because "GitHub could not be reached" and "nothing has been
 * pushed lately" deserve different words and only the caller knows which it is.
 */
export function CurrentlyBuilding({ items, updatedAt }: CurrentlyBuildingProps) {
  if (items.length === 0) return null;

  return (
    <>
      {updatedAt ? (
        <p className="mb-5 inline-flex items-center gap-2 font-mono text-chip text-mute">
          <i
            aria-hidden="true"
            className="size-2 shrink-0 rounded-full bg-ok [--dot-color:var(--ok)] motion-safe:animate-[dot-pulse_2s_ease-in-out_infinite]"
          />
          updated {formatCompactRelativeTime(updatedAt)}
        </p>
      ) : null}

      <ul role="list" className="list-none">
        {items.map((item) => (
          <li
            key={`${item.type}-${item.url}`}
            className="grid grid-cols-[36px_minmax(0,1fr)] items-start gap-3.5 border-t border-line py-4 md:grid-cols-[36px_minmax(0,1fr)_auto]"
          >
            <span className="flex size-9 items-center justify-center rounded-sm border border-line text-ac1">
              <ActivityIcon type={item.type} />
            </span>

            <div className="min-w-0">
              {/* The title links out: the row is about one commit, pull
                  request or release, and that thing has a URL. */}
              <a
                href={item.url}
                rel="noreferrer"
                target="_blank"
                className="text-[15px] leading-[1.45] font-semibold no-underline hover:underline"
              >
                {item.title}
              </a>
              <p className="mt-1 font-mono text-chip text-mute">
                {item.repo} · {typeLabels[item.type]}
              </p>
            </div>

            <time
              dateTime={item.occurredAt}
              className="col-start-2 font-mono text-chip whitespace-nowrap text-mute md:col-start-3"
            >
              {formatCompactRelativeTime(item.occurredAt)}
            </time>
          </li>
        ))}
      </ul>
    </>
  );
}
