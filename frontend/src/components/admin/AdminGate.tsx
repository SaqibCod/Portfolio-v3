"use client";

import { useState } from "react";

import { ModerationQueue } from "@/components/admin/ModerationQueue";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { cn } from "@/lib/cn";
import {
  openModerationQueue,
  type ModerationPages,
} from "@/lib/admin-guestbook";

type Unlocked = {
  token: string;
  pages: ModerationPages;
};

/**
 * `.lock` and `.top` from the admin export: the token prompt, and what it opens.
 *
 * The token lives in this component's state and nowhere else — no
 * `localStorage`, no `sessionStorage`, no cookie, no URL. That is the whole
 * design of the admin area: closing the tab or reloading the page is what signs
 * the owner out, and there is nothing left behind on the machine to steal. It
 * is passed down to {@link ModerationQueue} as a prop rather than put in a
 * context, so the set of things that can read it stays visible in one file.
 */
export function AdminGate() {
  const [token, setToken] = useState("");
  const [show, setShow] = useState(false);
  const [checking, setChecking] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [unlocked, setUnlocked] = useState<Unlocked | null>(null);
  /** Why the gate is closed again, or `null` on a first visit. */
  const [lockNotice, setLockNotice] = useState<string | null>(null);

  async function attemptUnlock() {
    const candidate = token.trim();
    if (!candidate) {
      setError("Paste your token first.");
      return;
    }

    setChecking(true);
    setError(null);
    const result = await openModerationQueue(candidate);
    setChecking(false);

    if (!result.ok) {
      setError(result.message);
      return;
    }

    setUnlocked({ token: candidate, pages: result.data });
    setLockNotice(null);
  }

  /**
   * Drops the token and everything loaded with it.
   *
   * The reason goes to the banner, not to the field error: a field error is
   * about what was just typed, and showing the same sentence in both places
   * reads as two separate problems.
   */
  function lock(reason: string | null) {
    setUnlocked(null);
    setToken("");
    setShow(false);
    setError(null);
    setLockNotice(
      reason ??
        "The token was cleared. Paste it again to continue.",
    );
  }

  if (unlocked) {
    return (
      <>
        <div className="mb-8 flex flex-wrap items-center justify-between gap-3">
          <Badge dot="ok">Token active · this tab only</Badge>
          <Button type="button" onClick={() => lock(null)}>
            <LockIcon />
            Lock
          </Button>
        </div>

        <ModerationQueue
          token={unlocked.token}
          initialPages={unlocked.pages}
          // A 401 mid-session means the token was rotated or revoked, so the
          // only correct response is to stop holding it.
          onUnauthorized={() =>
            lock("The server rejected that token. Paste it again to continue.")
          }
        />
      </>
    );
  }

  return (
    <div className="mx-auto w-full max-w-[460px] rounded-xl border border-line bg-bg2 p-6 md:rounded-2xl md:p-9">
      <span
        aria-hidden="true"
        className="mb-6 flex size-16 items-center justify-center rounded-lg grad text-white"
      >
        <BigLockIcon />
      </span>

      <p className="eyebrow">Owner only</p>
      <h1 className="display metal mt-3 text-[34px] md:text-[44px]">Admin</h1>
      <p className="mt-3 mb-7 text-[15px] leading-relaxed text-mute">
        Paste your admin token to moderate the guestbook.
      </p>

      {lockNotice ? (
        <div
          role="alert"
          className="mb-5 flex items-start gap-3 rounded-[16px] border border-warn bg-bg px-4 py-3.5 text-sm leading-relaxed"
        >
          <WarnIcon />
          <span>
            <b className="block font-semibold">Locked</b>
            {lockNotice}
          </span>
        </div>
      ) : null}

      <form
        aria-label="Unlock admin"
        onSubmit={(event) => {
          event.preventDefault();
          void attemptUnlock();
        }}
        className="flex flex-col gap-2"
      >
        <label
          htmlFor="admin-token"
          className="font-mono text-chip uppercase tracking-[0.08em] text-mute"
        >
          Admin token
        </label>

        <div className="relative flex">
          <input
            id="admin-token"
            name="admin-token"
            // Toggled rather than fixed: a pasted secret is worth being able to
            // check, and this is the owner's own screen.
            type={show ? "text" : "password"}
            value={token}
            onChange={(event) => {
              setToken(event.target.value);
              setError(null);
            }}
            // Not a sign-in credential and not something a password manager
            // should offer to save, since it is meant to live in one tab only.
            autoComplete="off"
            spellCheck={false}
            placeholder="Paste token…"
            aria-describedby={error ? "admin-token-error" : undefined}
            aria-invalid={error ? true : undefined}
            className={cn(
              "min-h-13 min-w-0 flex-1 rounded-input border bg-bg py-3.5 pr-14 pl-4",
              "font-mono text-[15px] leading-snug text-fg placeholder:text-mute",
              "transition-[border-color,box-shadow] duration-150",
              "focus-visible:border-ac1 focus-visible:shadow-[0_0_0_3px_var(--ring)] focus-visible:outline-none",
              error ? "border-err" : "border-line",
            )}
          />
          <button
            type="button"
            onClick={() => setShow(!show)}
            aria-label={show ? "Hide token" : "Show token"}
            aria-pressed={show}
            className="absolute top-1 right-1 flex size-11 cursor-pointer items-center justify-center rounded-sm border-0 bg-transparent text-mute hover:text-fg"
          >
            <EyeIcon hidden={show} />
          </button>
        </div>

        {error ? (
          <p
            id="admin-token-error"
            role="alert"
            className="flex items-center gap-1.5 text-[13px] text-err-text"
          >
            <SmallWarnIcon />
            {error}
          </p>
        ) : null}

        <Button
          type="submit"
          variant="primary"
          disabled={checking}
          className="mt-2.5 min-h-12.5 w-full"
        >
          {checking ? "Checking…" : "Unlock"}
        </Button>
      </form>

      <ul className="mt-5.5 flex flex-col gap-2.5 border-t border-line pt-4.5">
        {[
          "Kept in memory only — gone when you reload or close this tab.",
          "Sent only as an Authorization header to /api/v1/admin.",
          "Never stored in the site’s code, cookies or local storage.",
        ].map((note) => (
          <li
            key={note}
            className="flex items-start gap-2.5 text-[13px] leading-snug text-mute"
          >
            <CheckIcon />
            {note}
          </li>
        ))}
      </ul>
    </div>
  );
}

function LockIcon() {
  return (
    <svg
      width="14"
      height="14"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.4"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <rect x="5" y="11" width="14" height="10" rx="2" />
      <path d="M8 11V7a4 4 0 0 1 8 0v4" />
    </svg>
  );
}

function BigLockIcon() {
  return (
    <svg
      width="28"
      height="28"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <rect x="5" y="11" width="14" height="10" rx="2" />
      <path d="M8 11V7a4 4 0 0 1 8 0v4" />
    </svg>
  );
}

type EyeIconProps = {
  hidden: boolean;
};

/** The eye, struck through once the token is on screen. */
function EyeIcon({ hidden }: EyeIconProps) {
  return (
    <svg
      width="18"
      height="18"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12z" />
      <circle cx="12" cy="12" r="3" />
      {hidden ? <path d="M3 3l18 18" /> : null}
    </svg>
  );
}

function WarnIcon() {
  return (
    <svg
      width="18"
      height="18"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.4"
      strokeLinecap="round"
      aria-hidden="true"
      className="mt-px shrink-0 text-warn"
    >
      <path d="M12 3L2 20h20z" />
      <path d="M12 10v4M12 17v.5" />
    </svg>
  );
}

function SmallWarnIcon() {
  return (
    <svg
      width="14"
      height="14"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.6"
      strokeLinecap="round"
      aria-hidden="true"
      className="shrink-0"
    >
      <circle cx="12" cy="12" r="9" />
      <path d="M12 8v5M12 16v.5" />
    </svg>
  );
}

function CheckIcon() {
  return (
    <svg
      width="14"
      height="14"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.4"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className="mt-0.5 shrink-0 text-ac1"
    >
      <path d="M5 12l5 5 9-10" />
    </svg>
  );
}
