"use client";

import { useState } from "react";

import { GuestbookEntryCard } from "@/components/guestbook/GuestbookEntryCard";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Spinner } from "@/components/ui/Spinner";
import {
  fetchMoreApprovedEntries,
  type GuestbookEntry,
  type GuestbookPage,
} from "@/lib/guestbook";

export type PendingEntry = {
  id: string;
  name: string;
  message: string;
  createdAt: string;
};

export type GuestbookListProps = {
  /** The first page, fetched on the server so the list is in the HTML. */
  initialPage: GuestbookPage;
  /**
   * The note this visitor has just left. Shown at the top of the list, marked
   * as awaiting approval — it is in nobody else's list until it is approved.
   */
  pendingEntry?: PendingEntry | null;
  /** Set when the first page could not be fetched, so the list says so. */
  unavailable?: boolean;
};

/**
 * `Latest notes`: the server-rendered first page, plus "Load more".
 *
 * A client component holding server-fetched props, rather than a server
 * component. The first page still arrives as HTML — a client component is
 * server-rendered too — so nothing here costs the page its indexability; the
 * interactivity is only what happens after that first paint.
 *
 * Appending rather than replacing, and no cursor in the URL: the design's
 * "Load more" grows one list, and putting a cursor in the address would make
 * the shareable URL of this page something other than its first page.
 */
export function GuestbookList({
  initialPage,
  pendingEntry = null,
  unavailable = false,
}: GuestbookListProps) {
  const [entries, setEntries] = useState<GuestbookEntry[]>(initialPage.items);
  const [cursor, setCursor] = useState<string | null>(initialPage.nextCursor);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function loadMore() {
    if (!cursor || loading) return;

    setLoading(true);
    setError(null);
    const result = await fetchMoreApprovedEntries(cursor);
    setLoading(false);

    if (!result.ok) {
      setError(result.message);
      return;
    }

    // Guards against a double-append if the same cursor is somehow spent twice.
    setEntries((current) => {
      const known = new Set(current.map((entry) => entry.id));
      return [...current, ...result.page.items.filter((e) => !known.has(e.id))];
    });
    setCursor(result.page.nextCursor);
  }

  const isEmpty = entries.length === 0 && !pendingEntry;

  return (
    <section aria-labelledby="latest-notes">
      <div className="mb-5 flex flex-wrap items-center justify-between gap-4">
        <h2 id="latest-notes" className="display text-[clamp(28px,3vw,40px)]">
          Latest notes
        </h2>
        <Badge>{countLabel(entries.length, cursor)}</Badge>
      </div>

      {unavailable ? (
        <p
          role="status"
          className="rounded-lg border border-warn bg-bg2 px-4 py-3.5 text-sm leading-relaxed"
        >
          The notes couldn’t be loaded just now. Reload the page to try again —
          you can still sign the guestbook.
        </p>
      ) : null}

      {isEmpty && !unavailable ? (
        <EmptyState />
      ) : (
        <>
          <div className="grid gap-4 md:grid-cols-2">
            {pendingEntry ? (
              <GuestbookEntryCard {...pendingEntry} pending />
            ) : null}
            {entries.map((entry) => (
              <GuestbookEntryCard key={entry.id} {...entry} />
            ))}
          </div>

          <div className="mt-8 flex flex-col items-center gap-3">
            {cursor ? (
              <Button type="button" onClick={() => void loadMore()} disabled={loading}>
                {loading ? (
                  <>
                    <Spinner />
                    Loading…
                  </>
                ) : (
                  "Load more notes"
                )}
              </Button>
            ) : null}

            {/* The outcome of a press, so it has to be announced and not just
                appear below the button. */}
            <p aria-live="polite" className="font-mono text-chip text-mute">
              {error ?? endNote(entries.length, cursor)}
            </p>
          </div>
        </>
      )}
    </section>
  );
}

/**
 * The chip beside the heading.
 *
 * The contract's page carries no total, only the items and a cursor, so an
 * exact count is only knowable once the list has been walked to the end. Until
 * then this says "20+" rather than claiming a total it cannot see.
 */
function countLabel(loaded: number, cursor: string | null): string {
  if (loaded === 0) return "Newest first";
  return cursor ? `${loaded}+ notes` : `${loaded} ${loaded === 1 ? "note" : "notes"}`;
}

function endNote(loaded: number, cursor: string | null): string {
  if (loaded === 0) return "";
  return cursor
    ? `Showing ${loaded} notes`
    : "You’ve reached the very first note.";
}

/** `.empty`: the dashed panel shown when nothing has been approved yet. */
function EmptyState() {
  return (
    <div className="flex flex-col items-center gap-3.5 rounded-xl border border-dashed border-line px-6 py-16 text-center">
      <h3 className="display text-[28px]">Be the first</h3>
      <p className="m-0 max-w-[380px] leading-relaxed text-mute">
        No notes yet. Sign the guestbook and yours will be the first one here
        once it’s approved.
      </p>
    </div>
  );
}
