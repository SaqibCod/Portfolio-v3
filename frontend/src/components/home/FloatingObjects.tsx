/**
 * `.obj` — the drifting shapes behind the About and Contact bands.
 *
 * Purely decorative, so the whole group is hidden from assistive tech and
 * never takes pointer events. The gradients are the `--orb-*` tokens, and
 * every animation is wrapped in `motion-safe:`.
 */
export function AboutObjects() {
  return (
    <div aria-hidden="true" className="pointer-events-none">
      {/* Violet sphere */}
      <span className="absolute top-10 -left-3.5 size-17.5 rounded-full bg-[image:var(--orb-sphere)] shadow-[0_30px_60px_-20px_var(--ring)] motion-safe:animate-[bob_7s_ease-in-out_infinite] md:top-27.5 md:left-[7%] md:size-30" />

      {/* Fuchsia ring: a conic gradient with its centre masked out */}
      <span className="absolute top-7.5 -right-2.5 size-19 rounded-full bg-[image:var(--orb-ring)] [mask:radial-gradient(farthest-side,transparent_calc(100%-16px),#000_calc(100%-15px))] motion-safe:animate-[bob_7s_ease-in-out_infinite] motion-safe:[animation-delay:-2s] md:top-22.5 md:right-[8%] md:size-32.5 md:[mask:radial-gradient(farthest-side,transparent_calc(100%-26px),#000_calc(100%-25px))]" />

      {/* Lime braces */}
      <span className="absolute bottom-10 left-[4%] bg-[image:var(--orb-glyph)] bg-clip-text font-mono text-[52px] leading-none font-semibold text-transparent motion-safe:animate-[bob_7s_ease-in-out_infinite] motion-safe:[animation-delay:-4s] md:bottom-27.5 md:left-[12%] md:text-[92px]">
        {"{ }"}
      </span>

      {/* Orange capsule */}
      <span className="absolute right-[2%] bottom-12.5 h-9 w-21.5 rotate-[-24deg] rounded-full bg-[image:var(--orb-capsule)] shadow-[0_26px_50px_-18px_rgb(234_88_12/0.55)] motion-safe:animate-[bobr_7s_ease-in-out_infinite] motion-safe:[animation-delay:-1s] md:right-[13%] md:bottom-30 md:h-15.5 md:w-37.5" />
    </div>
  );
}

/** The two shapes on the inverted contact band. */
export function ContactObjects() {
  return (
    <div aria-hidden="true" className="pointer-events-none">
      <span className="absolute top-30 -left-7.5 size-22.5 rounded-full bg-[image:var(--orb-sphere)] motion-safe:animate-[bob_8s_ease-in-out_infinite] md:top-90 md:-left-10 md:size-37.5" />
      <span className="absolute top-7.5 right-[4%] h-25 w-12.5 rotate-[32deg] rounded-full bg-[image:var(--orb-glyph)] motion-safe:animate-[bobr_7s_ease-in-out_infinite] motion-safe:[animation-delay:-3s] md:top-15 md:right-[6%] md:h-45 md:w-22.5" />
    </div>
  );
}
