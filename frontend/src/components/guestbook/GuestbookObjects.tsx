/**
 * `.o1` and `.o2` — the two shapes drifting behind the guestbook head.
 *
 * Purely decorative, so the group is hidden from assistive tech and never
 * takes pointer events. The capsule is the same `--orb-glyph` lime the contact
 * band uses; the sphere is the export's fuchsia variant, which is why it has a
 * token of its own rather than reusing the violet `--orb-sphere`.
 */
export function GuestbookObjects() {
  return (
    <div aria-hidden="true" className="pointer-events-none">
      {/* Fuchsia sphere */}
      <span className="absolute top-7.5 right-[2%] size-16 rounded-full bg-[image:var(--orb-sphere-fuchsia)] shadow-[0_30px_60px_-20px_rgb(192_38_211/0.6)] motion-safe:animate-[bob_7s_ease-in-out_infinite] md:top-17.5 md:right-[8%] md:size-30" />

      {/* Lime capsule */}
      <span className="absolute top-30 right-[24%] h-16 w-7.5 rotate-[32deg] rounded-full bg-[image:var(--orb-glyph)] motion-safe:animate-[bobr_8s_ease-in-out_infinite] motion-safe:[animation-delay:-3s] md:top-57.5 md:right-[20%] md:h-30 md:w-14" />
    </div>
  );
}
