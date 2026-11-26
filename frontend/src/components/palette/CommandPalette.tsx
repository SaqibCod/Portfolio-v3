"use client";

import { Command as Cmdk, useCommandState } from "cmdk";
import { useCallback, useMemo, useState } from "react";

import { Kbd } from "@/components/ui/Kbd";

import type { CommandContext } from "@/lib/commands/context";
import type { Command, CommandGroupName } from "@/lib/commands/registry";

/**
 * `.pal` from the palette export: Cmd/Ctrl+K search over every page, project
 * and action the site has.
 *
 * Built on cmdk, which brings the combobox semantics — `role="listbox"`,
 * `aria-activedescendant` tracking the highlighted row, arrow keys, fuzzy
 * scoring — and on the Radix dialog underneath it, which brings the focus trap,
 * the scroll lock, Escape, and returning focus to whatever opened it. None of
 * that is re-implemented here; what is here is the design and the registry.
 *
 * This whole module, cmdk included, sits in a chunk that is only fetched once
 * the browser is idle. See `CommandLauncher`.
 */

const GROUP_ORDER: CommandGroupName[] = ["Pages", "Projects", "Actions"];

function SearchIcon() {
  return (
    <svg
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.2"
      strokeLinecap="round"
      aria-hidden="true"
      className="shrink-0"
    >
      <circle cx="11" cy="11" r="7" />
      <path d="M20 20l-3.5-3.5" />
    </svg>
  );
}

/** One glyph per `CommandKind`, exactly as the export assigns them. */
function CommandIcon({ kind }: { kind: Command["kind"] }) {
  if (kind === "page") {
    return (
      <svg
        width="16"
        height="16"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden="true"
      >
        <path d="M14 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z" />
        <path d="M14 3v6h6" />
      </svg>
    );
  }

  if (kind === "project") {
    return (
      <svg
        width="16"
        height="16"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden="true"
      >
        <rect x="3" y="4" width="18" height="14" rx="2" />
        <path d="M8 21h8M12 18v3" />
      </svg>
    );
  }

  if (kind === "terminal") {
    return (
      <svg
        width="16"
        height="16"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden="true"
      >
        <path d="M4 7l5 5-5 5M12 18h8" />
      </svg>
    );
  }

  return (
    <svg
      width="16"
      height="16"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M13 2L4 14h7l-1 8 9-12h-7z" />
    </svg>
  );
}

function SparkleIcon() {
  return (
    <svg
      width="16"
      height="16"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z" />
    </svg>
  );
}

/**
 * The export's `.item`, shared by the rows and by the empty state's Ask row.
 *
 * There is no `:hover` rule, deliberately: cmdk selects a row on pointer-enter,
 * so `data-selected` already covers hovering, and a second background rule at
 * the same specificity would leave which one wins up to stylesheet order.
 */
const ITEM_CLASS =
  "group flex min-h-14 w-full cursor-pointer touch-manipulation items-center " +
  "gap-3.5 rounded-md border-0 bg-transparent px-3 text-left text-fg " +
  "select-none data-[selected=true]:bg-sel md:min-h-13";

/** `.ico`. Picks up the accent from the row's selected state. */
const ICON_CLASS =
  "flex size-[34px] shrink-0 items-center justify-center rounded-sm " +
  "border border-line text-mute " +
  "group-data-[selected=true]:border-ac1 group-data-[selected=true]:text-ac1";

type PaletteProps = {
  /** The registry, already built for this theme and device. */
  commands: readonly Command[];
  context: CommandContext;
  /** On a touch device the palette is a sheet and `esc` is a close button. */
  touch: boolean;
  onClose: () => void;
};

export function CommandPalette({
  commands,
  context,
  touch,
  onClose,
}: PaletteProps) {
  const [query, setQuery] = useState("");

  const groups = useMemo(
    () =>
      GROUP_ORDER.map((label) => ({
        label,
        items: commands.filter((command) => command.group === label),
      })).filter((group) => group.items.length > 0),
    [commands],
  );

  const run = useCallback(
    (command: Command) => {
      // Closed first, so focus is already back where it came from before a
      // navigation or the chat panel takes over.
      onClose();
      command.run(context);
    },
    [context, onClose],
  );

  const askAi = useCallback(() => {
    onClose();
    context.openChat(query.trim() || undefined);
  }, [context, onClose, query]);

  return (
    <Cmdk.Dialog
      open
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
      label="Command palette"
      // Arrow keys wrap: with three short groups, stopping dead at the last
      // row is more surprising than returning to the first.
      loop
      overlayClassName="fixed inset-0 z-40 bg-scrim backdrop-blur-[6px]"
      // Bottom sheet below 760px, centred dialog above it — both from the
      // export, including the sheet's squared-off bottom edge.
      contentClassName={
        "fixed bottom-0 left-0 z-40 flex max-h-[82vh] w-full flex-col " +
        "overflow-hidden rounded-t-card border border-b-0 border-line " +
        "bg-bg2 pb-[env(safe-area-inset-bottom)] shadow-palette " +
        "md:top-[14vh] md:bottom-auto md:left-1/2 md:max-h-[70vh] " +
        "md:w-[min(640px,calc(100%-2rem))] md:-translate-x-1/2 " +
        "md:rounded-palette md:border-b md:pb-0"
      }
      className="flex min-h-0 flex-1 flex-col"
    >
      {/* `.grab` — the sheet's drag handle. Decoration: the sheet is dismissed
          by the close button, the scrim or Escape, never by dragging. */}
      <div
        aria-hidden="true"
        className="mx-auto mt-2.5 h-[5px] w-10 shrink-0 rounded-[3px] bg-line md:hidden"
      />

      <SearchRow
        query={query}
        onQueryChange={setQuery}
        touch={touch}
        onClose={onClose}
        onAskAi={askAi}
      />

      <Cmdk.List
        label="Pages, projects and actions"
        className="overflow-y-auto overscroll-contain p-2"
      >
        <Cmdk.Empty>
          <div className="flex flex-col items-center gap-2 px-5 py-9 text-center">
            <b className="text-[16px] font-semibold">
              No results for &ldquo;{query}&rdquo;
            </b>
            <span className="text-[14px] text-mute">
              Try &ldquo;projects&rdquo;, &ldquo;theme&rdquo; or
              &ldquo;email&rdquo;.
            </span>
            {/* Not a cmdk item — there are none left to highlight — so it is a
                real button, and Enter in the field reaches it too. */}
            <button
              type="button"
              onClick={askAi}
              className={`${ITEM_CLASS} mt-2.5 max-w-[420px] bg-sel`}
            >
              <span className={ICON_CLASS}>
                <SparkleIcon />
              </span>
              <span className="min-w-0 flex-1">
                <b className="block truncate text-[15px] font-semibold">
                  Ask the AI about &ldquo;{query}&rdquo;
                </b>
                <span className="mt-0.5 block truncate text-[12px] text-mute">
                  Opens the chat with your question
                </span>
              </span>
              {touch ? null : (
                <span className="shrink-0 font-mono text-[12px]">&#8629;</span>
              )}
            </button>
          </div>
        </Cmdk.Empty>

        {groups.map((group) => (
          <Cmdk.Group
            key={group.label}
            // `.grp`. A node rather than a string so the heading carries its
            // own styling; cmdk's wrapper element has none.
            heading={
              <span className="block px-3 pt-3 pb-1.5 font-mono text-[11px] tracking-[0.1em] text-mute uppercase">
                {group.label}
              </span>
            }
          >
            {group.items.map((command) => (
              <Cmdk.Item
                key={command.id}
                value={command.title}
                keywords={[
                  ...command.keywords,
                  command.subtitle,
                  command.group,
                ]}
                onSelect={() => run(command)}
                className={ITEM_CLASS}
              >
                <span className={ICON_CLASS}>
                  <CommandIcon kind={command.kind} />
                </span>
                <span className="min-w-0 flex-1">
                  <b className="block truncate text-[15px] font-semibold">
                    {command.title}
                  </b>
                  <span className="mt-0.5 block truncate text-[12px] text-mute">
                    {command.subtitle}
                  </span>
                </span>
                {/* The export shows the Enter glyph on the highlighted row
                    only, and never on a device with no Enter key. */}
                {touch ? null : (
                  <span className="hidden shrink-0 font-mono text-[12px] group-data-[selected=true]:inline">
                    &#8629;
                  </span>
                )}
              </Cmdk.Item>
            ))}
          </Cmdk.Group>
        ))}
      </Cmdk.List>

      <PaletteFooter />
    </Cmdk.Dialog>
  );
}

type SearchRowProps = {
  query: string;
  onQueryChange: (query: string) => void;
  touch: boolean;
  onClose: () => void;
  onAskAi: () => void;
};

/** `.search`. A child of the dialog so it can read cmdk's filtered count. */
function SearchRow({
  query,
  onQueryChange,
  touch,
  onClose,
  onAskAi,
}: SearchRowProps) {
  const count = useCommandState((state) => state.filtered.count);

  return (
    <div className="flex min-h-15 shrink-0 items-center gap-3 border-b border-line px-4.5 md:min-h-16">
      <span className="text-mute">
        <SearchIcon />
      </span>
      <Cmdk.Input
        autoFocus
        // No aria-label here, though the export has one: cmdk writes its own
        // `aria-labelledby` after the spread, pointing at the hidden <label>
        // it renders from the dialog's `label`, and labelledby outranks
        // aria-label. So the field is named "Command palette" like the dialog
        // around it. Redundant, but accurate — and the alternative is
        // reaching into the DOM to strip an attribute React would put back.
        value={query}
        onValueChange={onQueryChange}
        onKeyDown={(event) => {
          // With nothing left to highlight, cmdk has nothing to select, so
          // Enter would do nothing at all. The export sends it to the chat
          // instead — the one thing that can still answer the question.
          if (event.key === "Enter" && count === 0) {
            event.preventDefault();
            onAskAi();
          }
        }}
        placeholder="Search pages, projects, actions…"
        className="min-w-0 flex-1 border-0 bg-transparent py-4.5 text-[17px] font-medium text-fg outline-none placeholder:text-mute md:text-[18px]"
      />
      {touch ? (
        <button
          type="button"
          onClick={onClose}
          aria-label="Close command palette"
          className="flex size-10 shrink-0 cursor-pointer touch-manipulation items-center justify-center rounded-full border border-line bg-transparent text-fg"
        >
          <svg
            width="14"
            height="14"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2.4"
            strokeLinecap="round"
            aria-hidden="true"
          >
            <path d="M6 6l12 12M18 6L6 18" />
          </svg>
        </button>
      ) : (
        <Kbd>esc</Kbd>
      )}
    </div>
  );
}

/**
 * `.pfoot` — the key legend and the result count. Hidden on the sheet, where
 * the export drops it: none of the three keys it names exists on a phone.
 */
function PaletteFooter() {
  const count = useCommandState((state) => state.filtered.count);

  return (
    <div className="hidden shrink-0 flex-wrap items-center gap-4 border-t border-line px-4.5 py-3 text-[12px] text-mute md:flex">
      <span className="inline-flex items-center gap-1.5">
        <Kbd>&#8593;</Kbd>
        <Kbd>&#8595;</Kbd> navigate
      </span>
      <span className="inline-flex items-center gap-1.5">
        <Kbd>&#8629;</Kbd> select
      </span>
      <span className="inline-flex items-center gap-1.5">
        <Kbd>esc</Kbd> close
      </span>
      <span className="ml-auto">
        {count} result{count === 1 ? "" : "s"}
      </span>
    </div>
  );
}
