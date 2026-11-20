"use client";

import { useEffect, useRef, useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import type { TurnstileInstance } from "@marsidev/react-turnstile";

import { TurnstileField } from "@/components/contact/TurnstileField";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Spinner } from "@/components/ui/Spinner";
import { Textarea } from "@/components/ui/Textarea";
import { cn } from "@/lib/cn";
import {
  guestbookSchema,
  submitGuestbookEntry,
  type GuestbookFormValues,
} from "@/lib/guestbook";

const NAME_LIMIT = 50;
const MESSAGE_LIMIT = 500;

type BannerTone = "warn" | "err";

type Banner = {
  tone: BannerTone;
  title: string;
  body: string;
};

const EMPTY_FORM: GuestbookFormValues = {
  name: "",
  message: "",
  website: "",
};

const NO_TOKEN_YET: Banner = {
  tone: "warn",
  title: "Still checking you’re human",
  body: "Give the check below a second to finish, then sign again.",
};

const TURNSTILE_TITLE = "Couldn’t verify you’re human";

export type SignedEntry = {
  id: string;
  name: string;
  message: string;
  createdAt: string;
};

export type GuestbookFormProps = {
  /**
   * Called once the API has accepted a note, with the note as it was sent.
   *
   * The response carries only an id and a status, so the values come from the
   * form rather than from the server — which is also what makes the pending
   * card local to this visitor rather than something anyone else could see.
   */
  onSigned?: (entry: SignedEntry) => void;
  className?: string;
};

/**
 * `.signbox` from the guestbook export: the sticky "Sign it" panel.
 *
 * Deliberately close to `ContactForm` — same validation flow, same banner
 * vocabulary, same single-use Turnstile token handling — and it shares that
 * form's `TurnstileField`. What differs is what happens on success: a contact
 * message is simply sent, while a note here has to come back as a visible
 * "waiting for approval" state, because the visitor will otherwise look for it
 * in the list and not find it.
 */
export function GuestbookForm({ onSigned, className }: GuestbookFormProps) {
  const [token, setToken] = useState<string | null>(null);
  const [banner, setBanner] = useState<Banner | null>(null);
  const [signed, setSigned] = useState(false);

  const widgetRef = useRef<TurnstileInstance | null>(null);
  const bannerRef = useRef<HTMLDivElement | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    setError,
    setFocus,
    control,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<GuestbookFormValues>({
    resolver: zodResolver(guestbookSchema),
    defaultValues: EMPTY_FORM,
  });

  // `useWatch` rather than `watch`: it subscribes to these two fields only, so
  // a keystroke re-renders the counters instead of the whole form — and unlike
  // `watch` it returns the value rather than a function, which is what lets the
  // React Compiler memoize this component at all.
  const name = useWatch({ control, name: "name" }) ?? "";
  const message = useWatch({ control, name: "message" }) ?? "";

  // A banner reports something the visitor did not cause from this spot on the
  // page, so moving focus to it is what makes it findable without sight.
  useEffect(() => {
    if (banner) bannerRef.current?.focus();
  }, [banner]);

  // A typed-but-unsent note is worth protecting; once it has been sent there
  // is nothing left to lose.
  useEffect(() => {
    if (!isDirty || signed) return;

    const warn = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [isDirty, signed]);

  async function onSubmit(values: GuestbookFormValues) {
    if (!token) {
      setBanner(NO_TOKEN_YET);
      return;
    }

    // Turnstile tokens are single-use: spend this one, then always ask the
    // widget for another, whatever the outcome.
    const spent = token;
    setToken(null);
    setBanner(null);

    const result = await submitGuestbookEntry(values, spent);
    widgetRef.current?.reset();

    if (result.ok) {
      onSigned?.({
        id: result.id,
        name: values.name,
        message: values.message,
        createdAt: new Date().toISOString(),
      });
      reset(EMPTY_FORM);
      setSigned(true);
      return;
    }

    if (result.kind === "fields") {
      Object.entries(result.fieldErrors).forEach(([field, text], index) => {
        setError(
          field as keyof GuestbookFormValues,
          { type: "server", message: text },
          // Only the first one takes focus, so the visitor lands on the thing
          // to fix rather than on the last field in the list.
          { shouldFocus: index === 0 },
        );
      });
      return;
    }

    setBanner({
      tone: result.kind === "rateLimit" ? "warn" : "err",
      title:
        result.kind === "rateLimit"
          ? "You’ve signed twice today"
          : result.kind === "turnstile"
            ? TURNSTILE_TITLE
            : "Note not sent",
      body: result.message,
    });
  }

  if (signed) {
    return (
      <SuccessPanel
        className={className}
        onReset={() => {
          setSigned(false);
          setBanner(null);
          widgetRef.current?.reset();
          setFocus("name");
        }}
      />
    );
  }

  return (
    <div className={className}>
      <h2 className="display mb-1.5 text-[32px]">Sign it</h2>
      <p className="mb-5.5 text-sm leading-relaxed text-mute">
        Your name and message will be public once approved.
      </p>

      <form
        noValidate
        aria-label="Guestbook form"
        // Built inside the handler rather than during render: `onSubmit` reads
        // a ref, and nothing may read a ref while rendering.
        onSubmit={(event) => void handleSubmit(onSubmit)(event)}
        className="flex flex-col gap-4"
      >
        {banner ? (
          <Alert ref={bannerRef} tone={banner.tone} title={banner.title}>
            {banner.body}
          </Alert>
        ) : null}

        <CountedField
          label="Name"
          count={name.length}
          limit={NAME_LIMIT}
          error={errors.name?.message}
        >
          {(fieldProps) => (
            <Input
              {...fieldProps}
              {...register("name")}
              type="text"
              autoComplete="name"
              placeholder="How should I credit you?"
              maxLength={NAME_LIMIT}
            />
          )}
        </CountedField>

        <CountedField
          label="Message"
          count={message.length}
          limit={MESSAGE_LIMIT}
          error={errors.message?.message}
        >
          {(fieldProps) => (
            <Textarea
              {...fieldProps}
              {...register("message")}
              placeholder="Write a few words…"
              maxLength={MESSAGE_LIMIT}
            />
          )}
        </CountedField>

        <Honeypot register={register} />

        <TurnstileField
          widgetRef={widgetRef}
          onToken={setToken}
          onClear={() => setToken(null)}
          invalid={banner?.title === TURNSTILE_TITLE}
          action="guestbook"
        />

        <Button
          type="submit"
          variant="primary"
          // Disabled only while the request is in flight. Gating it on the bot
          // check instead would leave a dead button during the second or two
          // the widget takes; clicking early is answered with a banner.
          disabled={isSubmitting}
          className="w-full"
        >
          {isSubmitting ? (
            <>
              <Spinner />
              Sending…
            </>
          ) : (
            "Sign the guestbook"
          )}
        </Button>

        <p className="flex items-start gap-2.5 text-[13px] leading-snug text-mute">
          <LockIcon />
          Held for approval before it’s public. Up to 2 notes per day.
        </p>
      </form>
    </div>
  );
}

type FieldControlProps = {
  id: string;
  "aria-describedby": string | undefined;
  "aria-invalid": boolean | undefined;
};

type CountedFieldProps = {
  label: string;
  count: number;
  limit: number;
  error?: string;
  children: (control: FieldControlProps) => React.ReactNode;
};

/**
 * `.fld` with the export's `n/limit` counter in the label row.
 *
 * A separate component from the contact form's `FormField` rather than a flag
 * on it: the counter changes the label from a single `<label>` into a row with
 * a second element, and the count has to be announced politely rather than on
 * every keystroke.
 */
function CountedField({
  label,
  count,
  limit,
  error,
  children,
}: CountedFieldProps) {
  const id = `gb-${label.toLowerCase()}`;
  const errorId = `${id}-error`;
  const countId = `${id}-count`;

  return (
    <div className="flex min-w-0 flex-col gap-2">
      <span className="flex justify-between gap-2 font-mono text-chip uppercase tracking-[0.08em] text-mute">
        <label htmlFor={id}>{label}</label>
        {/* Tabular figures so the number does not jitter as it is typed. */}
        <span id={countId} className="tabular-nums" aria-live="polite">
          {count}/{limit}
        </span>
      </span>

      {children({
        id,
        "aria-describedby": error ? `${errorId} ${countId}` : countId,
        "aria-invalid": error ? true : undefined,
      })}

      {error ? (
        <p
          id={errorId}
          className="flex items-center gap-1.5 text-[13px] text-err-text"
        >
          <WarningIcon />
          {error}
        </p>
      ) : null}
    </div>
  );
}

type HoneypotProps = {
  register: ReturnType<typeof useForm<GuestbookFormValues>>["register"];
};

/**
 * The contract's `website` honeypot: a real input that no person ever sees.
 *
 * Positioned off-screen rather than `display: none`, because some bots skip
 * fields they can tell are hidden. `aria-hidden` and `tabIndex={-1}` keep it
 * away from screen readers and the tab order, and `autoComplete="off"` keeps a
 * password manager from filling it in and getting a real visitor dropped.
 */
function Honeypot({ register }: HoneypotProps) {
  return (
    <div
      aria-hidden="true"
      className="pointer-events-none absolute -left-[9999px] size-0 overflow-hidden"
    >
      <label htmlFor="guestbook-website">Website</label>
      <input
        {...register("website")}
        id="guestbook-website"
        type="text"
        tabIndex={-1}
        autoComplete="off"
      />
    </div>
  );
}

type AlertProps = {
  tone: BannerTone;
  title: string;
  children: React.ReactNode;
  ref: React.Ref<HTMLDivElement>;
};

/** `.alert` from the exports: a hairline panel whose border carries the tone. */
function Alert({ tone, title, children, ref }: AlertProps) {
  return (
    <div
      ref={ref}
      role="alert"
      // Focusable only programmatically: it is moved to when it appears, but
      // it is not a stop on the way through the form.
      tabIndex={-1}
      className={cn(
        "flex items-start gap-3 rounded-[16px] border bg-bg px-4 py-3.5",
        "text-sm leading-relaxed",
        tone === "warn" ? "border-warn" : "border-err",
      )}
    >
      {tone === "warn" ? <ClockIcon /> : <AlertIcon />}
      <span>
        <b className="mb-0.5 block font-semibold">{title}</b>
        {children}
      </span>
    </div>
  );
}

type SuccessPanelProps = {
  className?: string;
  onReset: () => void;
};

/** `.okbox` from the export: what replaces the form once a note is in. */
function SuccessPanel({ className, onReset }: SuccessPanelProps) {
  return (
    <div role="status" className={cn("flex flex-col items-start gap-3.5", className)}>
      <span className="flex size-14 items-center justify-center rounded-full grad text-white">
        <CheckIcon />
      </span>
      <Badge dot="warn">Pending approval</Badge>
      <h2 className="display text-[26px]">Thanks for signing!</h2>
      <p className="m-0 leading-relaxed text-mute">
        Your note is waiting for me to approve it. It’ll appear here once it’s
        live — usually within a day.
      </p>
      <Button type="button" onClick={onReset}>
        Write another
      </Button>
    </div>
  );
}

function CheckIcon() {
  return (
    <svg
      width="26"
      height="26"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="3"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M5 12l5 5 9-10" />
    </svg>
  );
}

function WarningIcon() {
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

function AlertIcon() {
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
      className="mt-px shrink-0 text-err"
    >
      <path d="M12 3L2 20h20z" />
      <path d="M12 10v4M12 17v.5" />
    </svg>
  );
}

function ClockIcon() {
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
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7v5l3 2" />
    </svg>
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
      strokeWidth="2.2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className="mt-0.5 shrink-0"
    >
      <rect x="5" y="11" width="14" height="10" rx="2" />
      <path d="M8 11V7a4 4 0 0 1 8 0v4" />
    </svg>
  );
}
