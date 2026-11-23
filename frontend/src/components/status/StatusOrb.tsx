import type { ServiceState } from "@/lib/status";

/**
 * `.o1` — the sphere drifting beside the status head, tinted by overall health.
 *
 * Purely decorative: it duplicates the banner directly below it, which states
 * the same thing in words on a coloured disc with an icon. So it is hidden from
 * assistive technology, and it is also the one piece of the page that is
 * allowed to be as stale as the server render — a reader will never notice that
 * a gradient settled on green a minute before the banner agreed.
 */
const orbs: Record<ServiceState, string> = {
  operational: "bg-[image:var(--orb-ok)] shadow-[var(--orb-ok-shadow)]",
  degraded: "bg-[image:var(--orb-warn)] shadow-[var(--orb-warn-shadow)]",
  down: "bg-[image:var(--orb-down)] shadow-[var(--orb-down-shadow)]",
  unknown: "bg-[image:var(--orb-off)]",
};

export function StatusOrb({ state }: { state: ServiceState }) {
  return (
    <div aria-hidden="true" className="pointer-events-none">
      <span
        className={`absolute top-10 right-[4%] size-16 rounded-full motion-safe:animate-[bob_7s_ease-in-out_infinite] md:top-20 md:right-[7%] md:size-30 ${orbs[state]}`}
      />
    </div>
  );
}
