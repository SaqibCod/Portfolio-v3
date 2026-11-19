import { cn } from "@/lib/cn";

type PendingSlotProps = {
  /** The mono eyebrow, naming what belongs here. */
  label: string;
  /** One line on what will fill the slot. */
  children: React.ReactNode;
  className?: string;
};

/**
 * A labelled, dashed panel standing in for a widget a later phase builds —
 * live GitHub stats, uptime history, the guestbook, the contact form.
 *
 * It is deliberately a visible placeholder rather than an empty div: the slot
 * holds its space in the layout, says what is coming, and never implies data
 * it does not have. Marked `role="note"` so assistive tech announces it as an
 * aside rather than as page content.
 */
export function PendingSlot({ label, children, className }: PendingSlotProps) {
  return (
    <div
      role="note"
      className={cn(
        // Opaque: these panels sit over the decorative shapes on the contact
        // band, and a translucent ground would let a gradient bleed through
        // the text.
        "flex flex-col gap-2 rounded-lg border border-line border-dashed bg-bg2 p-5 md:rounded-card md:p-7",
        className,
      )}
    >
      <p className="eyebrow">{label}</p>
      <p className="max-w-[60ch] text-[15px] leading-relaxed text-mute">
        {children}
      </p>
    </div>
  );
}
