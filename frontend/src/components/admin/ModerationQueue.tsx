"use client";

import { useCallback, useEffect, useRef, useState } from "react";

import { avatarToken } from "@/components/guestbook/GuestbookEntryCard";
import { Badge, type BadgeTone } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Spinner } from "@/components/ui/Spinner";
import { cn } from "@/lib/cn";
import { formatRelativeTime } from "@/lib/format";
import {
  deleteEntry,
  listForModeration,
  moderateEntry,
  MODERATION_STATUSES,
  type AdminGuestbookEntry,
  type AdminResult,
  type ModerationDecision,
  type ModerationPages,
  type ModerationStatus,
} from "@/lib/admin-guestbook";

const STATUS_LABEL: Record<ModerationStatus, string> = {
  pending: "Pending",
  approved: "Approved",
  rejected: "Rejected",
};

const STATUS_TONE: Record<ModerationStatus, BadgeTone> = {
  pending: "warn",
  approved: "ok",
  rejected: "off",
};

/** The same three colours as `STATUS_TONE`, for the bare dot on a stat tile. */
const STATUS_DOT: Record<ModerationStatus, string> = {
  pending: "bg-warn",
  approved: "bg-ok",
  rejected: "bg-off",
};

export type ModerationQueueProps = {
  /** The owner's token, held in `AdminGate`'s state and never persisted. */
  token: string;
  /**
   * The first page of each status, already fetched by the unlock that proved
   * the token — so all three counts are real from the first paint and there is
   * nothing left to load on mount.
   */
  initialPages: ModerationPages;
  /** Called when the server rejects the token, so the gate can close again. */
  onUnauthorized: () => void;
};

/** Loaded entries by id, so an entry has one home however many tabs show it. */
type EntryMap = Map<string, AdminGuestbookEntry>;

/**
 * `.dash` from the admin export: counts, status tabs, and the three actions.
 *
 * One flat map of everything loaded rather than a list per tab. Moderating an
 * entry is then a single change to that entry's status, and the tab it appears
 * under and all three counts follow from it — no bucket to move it between and
 * no second request to put the lists back in agreement.
 *
 * Each action is applied locally first and reverted if the request fails. The
 * owner is working through a queue, and a round trip of latency per decision is
 * what makes that tedious; a decision that silently did not stick would be far
 * worse, which is why a failure puts the entry back and says so.
 */
export function ModerationQueue({
  token,
  initialPages,
  onUnauthorized,
}: ModerationQueueProps) {
  const [entries, setEntries] = useState<EntryMap>(() =>
    toMap(MODERATION_STATUSES.flatMap((status) => initialPages[status].items)),
  );
  const [cursors, setCursors] = useState<
    Record<ModerationStatus, string | null>
  >(() => ({
    pending: initialPages.pending.nextCursor,
    approved: initialPages.approved.nextCursor,
    rejected: initialPages.rejected.nextCursor,
  }));
  const [tab, setTab] = useState<ModerationStatus>("pending");
  const [busy, setBusy] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [confirming, setConfirming] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);

  const toastTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const flash = useCallback((message: string) => {
    setToast(message);
    if (toastTimer.current) clearTimeout(toastTimer.current);
    toastTimer.current = setTimeout(() => setToast(null), 3000);
  }, []);

  useEffect(
    () => () => {
      if (toastTimer.current) clearTimeout(toastTimer.current);
    },
    [],
  );

  /** Unwraps a result, closing the gate on a 401 and reporting anything else. */
  const handled = useCallback(
    <T,>(result: AdminResult<T>): T | null => {
      if (result.ok) return result.data;
      if (result.kind === "unauthorized") {
        onUnauthorized();
        return null;
      }
      flash(result.message);
      return null;
    },
    [flash, onUnauthorized],
  );

  const loadStatus = useCallback(
    async (status: ModerationStatus, cursor?: string) => {
      setLoading(true);
      const page = handled(await listForModeration(token, status, cursor));
      setLoading(false);

      if (!page) return;
      setEntries((current) => {
        const next = new Map(current);
        for (const entry of page.items) next.set(entry.id, entry);
        return next;
      });
      setCursors((current) => ({ ...current, [status]: page.nextCursor }));
    },
    [handled, token],
  );

  async function decide(entry: AdminGuestbookEntry, status: ModerationDecision) {
    const previous = entry;
    setBusy(entry.id);
    setEntries((current) =>
      new Map(current).set(entry.id, { ...entry, status }),
    );

    const result = await moderateEntry(token, entry.id, status);
    setBusy(null);

    if (!result.ok) {
      // Undo: put the entry back exactly as it was before the click.
      setEntries((current) => new Map(current).set(previous.id, previous));
      if (result.kind === "unauthorized") {
        onUnauthorized();
      } else {
        flash(`${result.message} Nothing was changed.`);
      }
      return;
    }

    setEntries((current) => new Map(current).set(result.data.id, result.data));
    flash(
      status === "approved"
        ? "Approved — now public"
        : previous.status === "approved"
          ? "Unpublished — hidden from the site"
          : "Rejected — stays hidden",
    );
  }

  async function remove(entry: AdminGuestbookEntry) {
    const previous = entry;
    setBusy(entry.id);
    setConfirming(null);
    setEntries((current) => {
      const next = new Map(current);
      next.delete(entry.id);
      return next;
    });

    const result = await deleteEntry(token, entry.id);
    setBusy(null);

    if (result.ok) {
      flash("Deleted permanently");
      return;
    }

    if (result.kind === "unauthorized") {
      setEntries((current) => new Map(current).set(previous.id, previous));
      onUnauthorized();
      return;
    }

    // A 404 means someone else got there first, so the entry really is gone and
    // removing it locally was right after all.
    if (result.kind === "notFound") {
      flash(result.message);
      return;
    }

    setEntries((current) => new Map(current).set(previous.id, previous));
    flash(`${result.message} Nothing was deleted.`);
  }

  const all = [...entries.values()];
  const countOf = (status: ModerationStatus) =>
    all.filter((entry) => entry.status === status).length;
  const shown = all
    .filter((entry) => entry.status === tab)
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  const tabCursor = cursors[tab] ?? null;

  return (
    <>
      <div className="mb-8">
        <p className="eyebrow">Guestbook</p>
        <h1 className="display metal mt-3 text-[clamp(40px,6vw,76px)]">
          Moderation
        </h1>
      </div>

      <div className="grid grid-cols-3 gap-2">
        {MODERATION_STATUSES.map((status) => (
          <StatTile
            key={status}
            status={status}
            count={countOf(status)}
            partial={Boolean(cursors[status])}
          />
        ))}
      </div>

      <StatusTabs
        current={tab}
        counts={{
          pending: countOf("pending"),
          approved: countOf("approved"),
          rejected: countOf("rejected"),
        }}
        onChange={(status) => {
          setTab(status);
          setConfirming(null);
        }}
      />

      <div
        id={`panel-${tab}`}
        role="tabpanel"
        aria-labelledby={`tab-${tab}`}
        tabIndex={-1}
      >
        {shown.length === 0 ? (
          <EmptyState status={tab} />
        ) : (
          <ul className="mt-6 flex list-none flex-col gap-3 p-0">
            {shown.map((entry) => (
              <li key={entry.id}>
                <ModerationCard
                  entry={entry}
                  busy={busy === entry.id}
                  confirming={confirming === entry.id}
                  onApprove={() => void decide(entry, "approved")}
                  onReject={() => void decide(entry, "rejected")}
                  onAskDelete={() => setConfirming(entry.id)}
                  onCancelDelete={() => setConfirming(null)}
                  onConfirmDelete={() => void remove(entry)}
                />
              </li>
            ))}
          </ul>
        )}

        {tabCursor ? (
          <div className="mt-6 flex justify-center">
            <Button
              type="button"
              disabled={loading}
              onClick={() => void loadStatus(tab, tabCursor)}
            >
              {loading ? (
                <>
                  <Spinner />
                  Loading…
                </>
              ) : (
                "Load more"
              )}
            </Button>
          </div>
        ) : null}
      </div>

      {/* `.toast`. The outcome of an action taken elsewhere on the page, so it
          has to be announced rather than only appear — and the container stays
          mounted whether or not there is a message, because a live region that
          appears at the same moment as its text is not reliably read out. */}
      <div aria-live="polite">
        {toast ? (
          <p className="fixed bottom-4 left-1/2 z-40 w-[calc(100%-2rem)] -translate-x-1/2 rounded-lg bg-fg px-5 py-3 text-center text-sm font-semibold text-bg shadow-float md:bottom-6 md:w-auto md:rounded-full md:whitespace-nowrap">
            {toast}
          </p>
        ) : null}
      </div>
    </>
  );
}

function toMap(items: AdminGuestbookEntry[]): EntryMap {
  return new Map(items.map((entry) => [entry.id, entry]));
}

type StatTileProps = {
  status: ModerationStatus;
  count: number;
  /** Set while more of this status may exist than has been counted. */
  partial: boolean;
};

/** `.stat`: one of the three counts above the tabs. */
function StatTile({ status, count, partial }: StatTileProps) {
  return (
    <div className="flex flex-col gap-1.5 rounded-md border border-line bg-bg2 px-3 py-3.5 md:rounded-lg md:px-5 md:py-4.5">
      <span className="flex items-center gap-2 font-mono text-[10px] uppercase tracking-[0.08em] text-mute md:text-[11px]">
        <i
          aria-hidden="true"
          className={cn("size-2 shrink-0 rounded-full", STATUS_DOT[status])}
        />
        {STATUS_LABEL[status]}
      </span>
      {/* Tabular figures so three counts side by side line up. */}
      <b className="display text-[26px] leading-none tabular-nums md:text-[36px]">
        {count}
        {partial ? "+" : ""}
      </b>
    </div>
  );
}

type StatusTabsProps = {
  current: ModerationStatus;
  counts: Record<ModerationStatus, number>;
  onChange: (status: ModerationStatus) => void;
};

/**
 * `.tabs`: the pending/approved/rejected filter.
 *
 * A real tablist, so arrow keys move between tabs and only the selected one is
 * a tab stop — which is what the pattern promises a screen-reader user, and
 * what three focusable buttons in a row would not deliver.
 */
function StatusTabs({ current, counts, onChange }: StatusTabsProps) {
  const listRef = useRef<HTMLDivElement | null>(null);

  function onKeyDown(event: React.KeyboardEvent<HTMLDivElement>) {
    const delta =
      event.key === "ArrowRight" ? 1 : event.key === "ArrowLeft" ? -1 : 0;
    if (delta === 0) return;

    event.preventDefault();
    const index = MODERATION_STATUSES.indexOf(current);
    const next =
      MODERATION_STATUSES[
        (index + delta + MODERATION_STATUSES.length) % MODERATION_STATUSES.length
      ];
    onChange(next);
    // Focus follows selection in an automatic-activation tablist.
    listRef.current
      ?.querySelector<HTMLButtonElement>(`#tab-${next}`)
      ?.focus();
  }

  return (
    <div
      ref={listRef}
      role="tablist"
      aria-label="Filter by status"
      onKeyDown={onKeyDown}
      className="mt-8 flex gap-1.5 overflow-x-auto border-b border-line"
    >
      {MODERATION_STATUSES.map((status) => {
        const selected = status === current;
        return (
          <button
            key={status}
            id={`tab-${status}`}
            type="button"
            role="tab"
            aria-selected={selected}
            aria-controls={`panel-${status}`}
            // Only the selected tab is a tab stop; the arrow keys do the rest.
            tabIndex={selected ? 0 : -1}
            onClick={() => onChange(status)}
            className={cn(
              "-mb-px inline-flex min-h-12 shrink-0 cursor-pointer items-center gap-2 border-0 border-b-2 bg-transparent px-4.5",
              "text-[13px] font-bold tracking-[0.06em] uppercase",
              "transition-colors duration-150",
              selected
                ? "border-b-ac1 text-fg"
                : "border-b-transparent text-mute hover:text-fg",
            )}
          >
            {STATUS_LABEL[status]}
            <small className="rounded-full border border-line px-2 py-0.5 font-mono text-[11px] font-normal tabular-nums">
              {counts[status]}
            </small>
          </button>
        );
      })}
    </div>
  );
}

type ModerationCardProps = {
  entry: AdminGuestbookEntry;
  busy: boolean;
  confirming: boolean;
  onApprove: () => void;
  onReject: () => void;
  onAskDelete: () => void;
  onCancelDelete: () => void;
  onConfirmDelete: () => void;
};

/** `.item`: one entry with its status and the decisions available on it. */
function ModerationCard({
  entry,
  busy,
  confirming,
  onApprove,
  onReject,
  onAskDelete,
  onCancelDelete,
  onConfirmDelete,
}: ModerationCardProps) {
  const decidedAt = entry.moderatedAt ?? entry.createdAt;

  return (
    <article
      className={cn(
        "flex flex-col gap-3.5 rounded-lg border border-line bg-bg2 p-4 md:rounded-[22px] md:p-5.5",
        busy && "opacity-60",
      )}
      aria-busy={busy || undefined}
    >
      <div className="flex flex-wrap items-center gap-3">
        <span
          aria-hidden="true"
          style={{ background: avatarToken(entry.id) }}
          className="display flex size-10.5 shrink-0 items-center justify-center rounded-full text-white"
        >
          {[...entry.name.trim()][0]?.toUpperCase() ?? "?"}
        </span>

        {/* min-w-0 so a long name truncates instead of pushing the chip out. */}
        <div className="min-w-0 flex-[1_1_200px]">
          <b className="block text-base font-semibold break-words">
            {entry.name}
          </b>
          <span className="font-mono text-chip text-mute">
            {STATUS_LABEL[entry.status].toLowerCase() === "pending"
              ? "submitted "
              : `${STATUS_LABEL[entry.status].toLowerCase()} `}
            <time dateTime={decidedAt}>{formatRelativeTime(decidedAt)}</time>
          </span>
        </div>

        <Badge dot={STATUS_TONE[entry.status]}>
          {STATUS_LABEL[entry.status]}
        </Badge>
      </div>

      <p className="m-0 leading-relaxed break-words whitespace-pre-line">
        {entry.message}
      </p>

      <div className="flex flex-wrap items-center justify-between gap-3 border-t border-line pt-3.5">
        <span className="font-mono text-[11px] text-mute">id {entry.id}</span>

        {confirming ? (
          <div
            role="alert"
            className="flex flex-wrap items-center gap-2.5 rounded-[16px] border border-err py-2.5 pr-2.5 pl-4 text-sm"
          >
            <b className="text-err-text">Delete permanently?</b>
            <span className="text-mute">This can’t be undone.</span>
            <Button type="button" onClick={onCancelDelete}>
              Cancel
            </Button>
            <Button
              type="button"
              onClick={onConfirmDelete}
              className="border-[#dc2626] bg-[#dc2626] text-white hover:border-[#b91c1c] hover:bg-[#b91c1c] hover:text-white"
            >
              Delete
            </Button>
          </div>
        ) : (
          <div className="flex flex-wrap gap-2 max-md:w-full">
            {entry.status !== "approved" ? (
              <Button
                type="button"
                variant="primary"
                disabled={busy}
                onClick={onApprove}
                className="max-md:flex-[1_1_30%] max-md:px-3"
              >
                Approve
              </Button>
            ) : null}

            {entry.status !== "rejected" ? (
              <Button
                type="button"
                disabled={busy}
                onClick={onReject}
                className="max-md:flex-[1_1_30%] max-md:px-3"
              >
                {entry.status === "approved" ? "Unpublish" : "Reject"}
              </Button>
            ) : null}

            <Button
              type="button"
              disabled={busy}
              onClick={onAskDelete}
              className="text-err-text hover:border-err hover:text-err-text max-md:flex-[1_1_30%] max-md:px-3"
            >
              Delete
            </Button>
          </div>
        )}
      </div>
    </article>
  );
}

type EmptyStateProps = {
  status: ModerationStatus;
};

/** `.empty`: inbox zero, or a status with nothing in it yet. */
function EmptyState({ status }: EmptyStateProps) {
  return (
    <div className="mt-6 flex flex-col items-center gap-3 rounded-card border border-dashed border-line px-6 py-14 text-center">
      <span
        aria-hidden="true"
        className="flex size-14 items-center justify-center rounded-full bg-ok text-white"
      >
        <svg
          width="26"
          height="26"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="3"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <path d="M5 12l5 5 9-10" />
        </svg>
      </span>
      <h2 className="display text-[26px]">
        {status === "pending" ? "Inbox zero" : "Nothing here"}
      </h2>
      <p className="m-0 text-mute">
        {status === "pending"
          ? "No notes waiting for review. New ones will email you."
          : `No ${STATUS_LABEL[status].toLowerCase()} entries yet.`}
      </p>
    </div>
  );
}
