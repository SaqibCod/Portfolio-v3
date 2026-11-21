"use client";

import { useEffect, useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import type { TurnstileInstance } from "@marsidev/react-turnstile";

import { FormField } from "@/components/contact/FormField";
import { TurnstileField } from "@/components/contact/TurnstileField";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Textarea } from "@/components/ui/Textarea";
import { cn } from "@/lib/cn";
import {
  contactSchema,
  submitContactMessage,
  type ContactFormValues,
} from "@/lib/contact";

type BannerTone = "warn" | "err";

type Banner = {
  tone: BannerTone;
  title: string;
  body: string;
};

const EMPTY_FORM: ContactFormValues = {
  name: "",
  email: "",
  subject: "",
  message: "",
  website: "",
};

const NO_TOKEN_YET: Banner = {
  tone: "warn",
  title: "Still checking you’re human",
  body: "Give the check below a second to finish, then send again.",
};

export type ContactFormProps = {
  /**
   * Heading level for the success panel. The form is used under an `h1` on
   * `/contact` and under an `h2` in the home page's contact band, so the
   * panel's heading has to follow whichever it sits beneath.
   */
  successHeadingLevel?: "h2" | "h3";
  className?: string;
};

/**
 * `POST /api/v1/contact`, as the form in section 07 of the home export.
 *
 * Client-side rather than a Server Action on purpose: the API applies its own
 * per-IP rate limit and passes the caller's address to Cloudflare, and
 * proxying the request through Next would hand it the server's address
 * instead, making every visitor look like the same one.
 */
export function ContactForm({
  successHeadingLevel = "h3",
  className,
}: ContactFormProps) {
  const [token, setToken] = useState<string | null>(null);
  const [banner, setBanner] = useState<Banner | null>(null);
  const [sent, setSent] = useState(false);

  const widgetRef = useRef<TurnstileInstance | null>(null);
  const bannerRef = useRef<HTMLDivElement | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    setError,
    setFocus,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<ContactFormValues>({
    resolver: zodResolver(contactSchema),
    defaultValues: EMPTY_FORM,
  });

  // A banner reports something the visitor did not cause from this spot on the
  // page, so moving focus to it is what makes it findable without sight.
  useEffect(() => {
    if (banner) bannerRef.current?.focus();
  }, [banner]);

  // A typed-but-unsent message is worth protecting; once it has been sent
  // there is nothing left to lose.
  useEffect(() => {
    if (!isDirty || sent) return;

    const warn = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [isDirty, sent]);

  async function onSubmit(values: ContactFormValues) {
    if (!token) {
      setBanner(NO_TOKEN_YET);
      return;
    }

    // Turnstile tokens are single-use: spend this one, then always ask the
    // widget for another, whatever the outcome.
    const spent = token;
    setToken(null);
    setBanner(null);

    const result = await submitContactMessage(values, spent);
    widgetRef.current?.reset();

    if (result.ok) {
      reset(EMPTY_FORM);
      setSent(true);
      return;
    }

    if (result.kind === "fields") {
      const entries = Object.entries(result.fieldErrors);
      entries.forEach(([field, message], index) => {
        setError(
          field as keyof ContactFormValues,
          { type: "server", message },
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
          ? "Too many messages"
          : result.kind === "turnstile"
            ? "Couldn’t verify you’re human"
            : "Message not sent",
      body: result.message,
    });
  }

  if (sent) {
    return (
      <SuccessPanel
        headingLevel={successHeadingLevel}
        className={className}
        onReset={() => {
          setSent(false);
          setBanner(null);
          widgetRef.current?.reset();
          setFocus("name");
        }}
      />
    );
  }

  return (
    <form
      noValidate
      aria-label="Contact form"
      // Built inside the handler rather than during render: `onSubmit` reads a
      // ref, and nothing may read a ref while rendering.
      onSubmit={(event) => void handleSubmit(onSubmit)(event)}
      className={cn("flex flex-col gap-[18px]", className)}
    >
      {banner ? (
        <Alert ref={bannerRef} tone={banner.tone} title={banner.title}>
          {banner.body}
        </Alert>
      ) : null}

      <div className="grid gap-[18px] md:grid-cols-2">
        <FormField label="Full name" error={errors.name?.message}>
          {(control) => (
            <Input
              {...control}
              {...register("name")}
              type="text"
              autoComplete="name"
              placeholder="Your name"
              maxLength={100}
            />
          )}
        </FormField>

        <FormField label="Email" error={errors.email?.message}>
          {(control) => (
            <Input
              {...control}
              {...register("email")}
              type="email"
              inputMode="email"
              autoComplete="email"
              spellCheck={false}
              placeholder="you@company.com"
              maxLength={254}
            />
          )}
        </FormField>
      </div>

      <FormField label="Subject (optional)" error={errors.subject?.message}>
        {(control) => (
          <Input
            {...control}
            {...register("subject")}
            type="text"
            autoComplete="off"
            placeholder="What’s it about?"
            maxLength={150}
          />
        )}
      </FormField>

      <FormField label="Message" error={errors.message?.message}>
        {(control) => (
          <Textarea
            {...control}
            {...register("message")}
            placeholder="At least 10 characters"
            maxLength={5000}
          />
        )}
      </FormField>

      <Honeypot register={register} />

      <TurnstileField
        widgetRef={widgetRef}
        onToken={setToken}
        onClear={() => setToken(null)}
        invalid={banner?.title === "Couldn’t verify you’re human"}
        action="contact"
        // Both places this form is used — /contact and the home page's contact
        // band — are inverted bands.
        invertedGround
      />

      <Button
        type="submit"
        variant="primary"
        // Disabled only while the request is in flight. Gating it on the bot
        // check instead would leave a dead button during the second or two the
        // widget takes; clicking early is answered with a banner that says so.
        disabled={isSubmitting}
        className="min-w-[180px] self-start max-md:w-full"
      >
        {isSubmitting ? (
          <>
            <Spinner />
            Sending…
          </>
        ) : (
          "Send message"
        )}
      </Button>
    </form>
  );
}

type HoneypotProps = {
  register: ReturnType<typeof useForm<ContactFormValues>>["register"];
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
      <label htmlFor="contact-website">Website</label>
      <input
        {...register("website")}
        id="contact-website"
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
        "flex items-start gap-3 rounded-[16px] border bg-bg2 px-4 py-3.5",
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
  headingLevel: "h2" | "h3";
  className?: string;
  onReset: () => void;
};

/** `.ok` from the export: the panel that replaces the form once it has sent. */
function SuccessPanel({
  headingLevel,
  className,
  onReset,
}: SuccessPanelProps) {
  const Heading = headingLevel;

  return (
    <div
      role="status"
      className={cn(
        "flex flex-col items-start gap-3.5 rounded-card border border-line bg-bg2 p-9",
        className,
      )}
    >
      <Badge dot="ok">Queued</Badge>
      <Heading className="display text-[28px]">Message sent</Heading>
      <p className="leading-relaxed text-mute">
        Thanks — it’s on its way. I’ll reply to the email you gave.
      </p>
      <Button type="button" onClick={onReset}>
        Send another
      </Button>
    </div>
  );
}

function Spinner() {
  return (
    <svg
      width="16"
      height="16"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.6"
      strokeLinecap="round"
      aria-hidden="true"
      className="motion-safe:animate-spin"
    >
      <path d="M21 12a9 9 0 1 1-6.2-8.6" />
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
