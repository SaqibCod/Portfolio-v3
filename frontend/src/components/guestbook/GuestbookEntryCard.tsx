import { Badge } from "@/components/ui/Badge";
import { cn } from "@/lib/cn";
import { formatTimestampDate } from "@/lib/format";

/**
 * The export's `AV` palette, as the tokens declared in `globals.css`.
 *
 * Picked from the entry's own id rather than its position in the list, so a
 * note keeps the same colour when another page is appended above or below it.
 */
const AVATAR_TOKENS = [
  "var(--av-1)",
  "var(--av-2)",
  "var(--av-3)",
  "var(--av-4)",
  "var(--av-5)",
  "var(--av-6)",
] as const;

/**
 * A stable index from an entry id.
 *
 * A plain character sum, not a real hash: the only requirements are that the
 * same id always gives the same colour and that adjacent ids usually differ.
 */
export function avatarToken(id: string): string {
  let sum = 0;
  for (let index = 0; index < id.length; index += 1) {
    sum += id.charCodeAt(index);
  }
  return AVATAR_TOKENS[sum % AVATAR_TOKENS.length];
}

/** The first character of a name, for the avatar disc. */
function initialOf(name: string): string {
  return [...name.trim()][0]?.toUpperCase() ?? "?";
}

type AvatarProps = {
  id: string;
  name: string;
};

/** `.av`: a 44px disc carrying the visitor's initial in display type. */
function Avatar({ id, name }: AvatarProps) {
  return (
    <span
      // Decorative: the name it stands for is written out right beside it, so
      // announcing the initial too would just read the same letter twice.
      aria-hidden="true"
      style={{ background: avatarToken(id) }}
      className="display flex size-11 shrink-0 items-center justify-center rounded-full text-white"
    >
      {initialOf(name)}
    </span>
  );
}

type WhoProps = {
  id: string;
  name: string;
  createdAt: string;
  children?: React.ReactNode;
};

/** `.who`: the avatar, the name and the date, with room for a trailing chip. */
function Who({ id, name, createdAt, children }: WhoProps) {
  return (
    <div className="flex items-center gap-3">
      <Avatar id={id} name={name} />
      <div className="min-w-0">
        <b className="block text-[15px] font-semibold break-words">{name}</b>
        <time
          dateTime={createdAt}
          className="font-mono text-chip text-mute"
        >
          {formatTimestampDate(createdAt)}
        </time>
      </div>
      {children}
    </div>
  );
}

export type GuestbookEntryCardProps = {
  id: string;
  name: string;
  message: string;
  createdAt: string;
  /**
   * Marks the note the visitor has just left, which only they can see. The
   * export draws it as `.card.mine`: a dashed accent border, full width, and
   * the message in `fg` rather than `mute`.
   */
  pending?: boolean;
  className?: string;
};

/**
 * `.card` from the guestbook export: one note on the public list.
 *
 * An `<article>` rather than a `<div>`: each note is a self-contained piece of
 * writing by someone other than the site's author, which is exactly what the
 * element is for.
 *
 * The two states put their parts in different orders, as the export does. An
 * approved note leads with a quote mark and the message, because the words are
 * the point; a pending one leads with who wrote it and the "awaiting approval"
 * chip, because the status is.
 */
export function GuestbookEntryCard({
  id,
  name,
  message,
  createdAt,
  pending = false,
  className,
}: GuestbookEntryCardProps) {
  return (
    <article
      className={cn(
        "flex min-w-0 flex-col gap-4 rounded-lg border bg-bg2 p-5 md:rounded-card md:p-6",
        pending
          ? "border-dashed border-ac1 md:col-span-full"
          : "border-line",
        className,
      )}
    >
      {pending ? (
        <Who id={id} name={name} createdAt={createdAt}>
          <Badge dot="warn" className="ml-auto">
            Awaiting approval · only you see this
          </Badge>
        </Who>
      ) : (
        <span
          aria-hidden="true"
          className="display h-5.5 text-[56px] leading-[0.5] text-ac1"
        >
          “
        </span>
      )}

      {/* `break-words` and `whitespace-pre-line`: the message is user-written,
          so it can be one unbroken 500-character string or several short
          paragraphs, and both have to survive the card. */}
      <p
        className={cn(
          "m-0 leading-relaxed break-words whitespace-pre-line",
          pending ? "text-fg" : "text-mute",
        )}
      >
        {message}
      </p>

      {pending ? null : <Who id={id} name={name} createdAt={createdAt} />}
    </article>
  );
}
