import { useId } from "react";

import { cn } from "@/lib/cn";

/** `.flab` / `.fld span`: a 12px uppercase JetBrains Mono field label. */
const labelClasses =
  "font-mono text-chip uppercase tracking-[0.08em] text-mute";

/** The props a field has to put on its control for the label and error to work. */
export type FieldControlProps = {
  id: string;
  "aria-describedby": string | undefined;
  "aria-invalid": boolean | undefined;
};

type FormFieldProps = {
  label: string;
  /** The message to show under the control, or `undefined` when it is valid. */
  error?: string;
  /** Optional hint, announced with the control. */
  hint?: string;
  className?: string;
  /**
   * Renders the control. Spread the supplied props onto it: that is what ties
   * the label, the hint and the error message to the right input without the
   * caller inventing its own ids.
   */
  children: (control: FieldControlProps) => React.ReactNode;
};

/**
 * `.fld` from the exports: a stacked label, control and error message.
 *
 * The label is a real `<label for>` rather than a styled span, so clicking it
 * focuses the control, and the error is wired through `aria-describedby` with
 * `aria-invalid`, so a screen reader announces the problem on focus instead of
 * leaving it as colour-only feedback.
 */
export function FormField({
  label,
  error,
  hint,
  className,
  children,
}: FormFieldProps) {
  const id = useId();
  const errorId = `${id}-error`;
  const hintId = `${id}-hint`;

  const describedBy =
    [error ? errorId : null, hint ? hintId : null].filter(Boolean).join(" ") ||
    undefined;

  return (
    <div className={cn("flex min-w-0 flex-col gap-2", className)}>
      <label htmlFor={id} className={labelClasses}>
        {label}
      </label>

      {children({
        id,
        "aria-describedby": describedBy,
        "aria-invalid": error ? true : undefined,
      })}

      {hint ? (
        <p id={hintId} className="text-[13px] leading-snug text-mute">
          {hint}
        </p>
      ) : null}

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

/** `.ferr svg`: the 14px exclamation circle the exports put before an error. */
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
