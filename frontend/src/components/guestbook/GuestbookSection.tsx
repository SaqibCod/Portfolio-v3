"use client";

import { useState } from "react";

import { GuestbookForm, type SignedEntry } from "@/components/guestbook/GuestbookForm";
import { GuestbookList } from "@/components/guestbook/GuestbookList";

import type { GuestbookPage } from "@/lib/guestbook";

export type GuestbookSectionProps = {
  /** The first page of approved notes, fetched on the server. */
  initialPage: GuestbookPage;
  /** Set when that fetch failed, so the list can say so and the form still works. */
  unavailable?: boolean;
};

/**
 * The two-column body of `/guestbook`: the sticky "Sign it" panel and the list.
 *
 * These are one client component rather than two because of a single piece of
 * shared state — the note the visitor has just left. The form is the only thing
 * that knows about it and the list is the only thing that can show it, and the
 * export puts it at the top of the list rather than inside the panel, so the
 * state has to live above both.
 *
 * The notes themselves still arrive as server-rendered HTML: `initialPage` is
 * fetched in the page component and rendered through here on the server before
 * any JavaScript runs.
 */
export function GuestbookSection({
  initialPage,
  unavailable = false,
}: GuestbookSectionProps) {
  const [signed, setSigned] = useState<SignedEntry | null>(null);

  return (
    <div className="grid items-start gap-10 pb-35 md:grid-cols-[400px_minmax(0,1fr)] md:pb-50">
      <aside
        aria-label="Sign the guestbook"
        className="rounded-card border border-line bg-bg2 p-5 md:sticky md:top-26 md:rounded-xl md:p-7"
      >
        <GuestbookForm onSigned={setSigned} />
      </aside>

      <GuestbookList
        initialPage={initialPage}
        pendingEntry={signed}
        unavailable={unavailable}
      />
    </div>
  );
}
