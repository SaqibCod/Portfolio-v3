import Image from "next/image";

import { cn } from "@/lib/cn";

type CoverFrameProps = {
  /** The real screenshot, once there is one. */
  cover?: { src: string; alt: string };
  /** Shown in the placeholder when there is no cover yet. */
  label: string;
  /** Renders the browser chrome bar the export draws above a screenshot. */
  chrome?: string;
  /** Set on the one cover above the fold, so it is not lazy-loaded. */
  priority?: boolean;
  className?: string;
};

function ChromeBar({ label }: { label: string }) {
  return (
    <div className="absolute inset-x-0 top-0 z-1 flex h-11 items-center gap-2 border-b border-line bg-bg2 px-4.5">
      <i aria-hidden="true" className="size-2.5 rounded-full bg-line" />
      <i aria-hidden="true" className="size-2.5 rounded-full bg-line" />
      <i aria-hidden="true" className="size-2.5 rounded-full bg-line" />
      <span className="ml-4 truncate font-mono text-chip text-mute">
        {label}
      </span>
    </div>
  );
}

/**
 * `.shot` / `.cover` — the framed screenshot slot.
 *
 * Until a project has a real cover image, this draws the export's graph-paper
 * placeholder with a bracketed label, so the layout is final and nothing
 * pretends to be a screenshot.
 */
export function CoverFrame({
  cover,
  label,
  chrome,
  priority = false,
  className,
}: CoverFrameProps) {
  return (
    <div
      className={cn(
        "relative flex items-center justify-center overflow-hidden rounded-lg border border-line bg-bg",
        !cover &&
          "[background-image:linear-gradient(var(--line)_1px,transparent_1px),linear-gradient(90deg,var(--line)_1px,transparent_1px)] [background-size:28px_28px]",
        className,
      )}
    >
      {chrome ? <ChromeBar label={chrome} /> : null}
      {cover ? (
        <Image
          src={cover.src}
          alt={cover.alt}
          width={1200}
          height={675}
          sizes="(min-width: 47.5rem) 680px, 100vw"
          priority={priority}
          loading={priority ? undefined : "lazy"}
          className="size-full object-cover"
        />
      ) : (
        <span className="z-1 rounded-xs border border-line bg-bg2 px-3 py-2 text-center font-mono text-chip text-mute">
          [{label}]
        </span>
      )}
    </div>
  );
}
