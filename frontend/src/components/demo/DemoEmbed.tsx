import { ButtonLink } from "@/components/ui/Button";

type DemoEmbedProps = {
  /** The app to embed. */
  src: string;
  /** Names the frame for screen readers and for the "open" link. */
  title: string;
  /** Mono caption under the frame, e.g. what to try in the demo. */
  caption?: string;
};

/**
 * `.demo` — a live project embedded in its own frame.
 *
 * The iframe is lazy and sandboxed: it sits well below the fold, so
 * `loading="lazy"` keeps it out of the LCP path, and the sandbox allows only
 * what a demo needs (its own scripts and same-origin storage) while still
 * blocking top-level navigation and downloads. A visible "open in a new tab"
 * link is always rendered, because an embedded app may refuse to frame at all.
 */
export function DemoEmbed({ src, title, caption }: DemoEmbedProps) {
  return (
    <figure className="mt-6">
      <div className="relative aspect-video overflow-hidden rounded-card border border-line bg-bg2">
        <iframe
          src={src}
          title={title}
          loading="lazy"
          // `allow-same-origin` is scoped to the embedded origin, not ours:
          // the frame is cross-origin, so it cannot reach this page's storage.
          sandbox="allow-scripts allow-same-origin allow-forms allow-popups"
          referrerPolicy="no-referrer"
          className="absolute inset-0 size-full border-0"
        />
      </div>
      <figcaption className="mt-3.5 flex flex-wrap items-center justify-between gap-3">
        {caption ? (
          <span className="font-mono text-chip text-mute">{caption}</span>
        ) : null}
        <ButtonLink href={src} target="_blank" rel="noreferrer">
          Open {title} in a new tab
        </ButtonLink>
      </figcaption>
    </figure>
  );
}

type DemoSlotProps = {
  /** What will eventually live here, e.g. "githMind demo". */
  label: string;
  /** Why it is not here yet. */
  note: string;
};

/**
 * The placeholder the design shows before a demo exists: the same frame
 * proportions, filled with a labelled grid rather than an empty iframe.
 */
export function DemoSlot({ label, note }: DemoSlotProps) {
  return (
    <div
      className="mt-6 flex aspect-video flex-col items-center justify-center gap-3 rounded-card border border-line border-dashed bg-bg px-5 text-center [background-image:linear-gradient(var(--line)_1px,transparent_1px),linear-gradient(90deg,var(--line)_1px,transparent_1px)] [background-size:28px_28px]"
      role="note"
    >
      <span className="rounded-xs border border-line bg-bg2 px-3 py-2 font-mono text-chip text-mute">
        [{label}]
      </span>
      <span className="max-w-[40ch] text-sm leading-relaxed text-mute">
        {note}
      </span>
    </div>
  );
}
