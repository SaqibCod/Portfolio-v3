import { formatTimestampDate } from "@/lib/format";
import {
  busiestDay,
  heatLevel,
  toContributionWeeks,
  type ContributionDay,
  type HeatLevel,
} from "@/lib/github";

/**
 * `.hm i.l1`–`.l4` from the exports, as tokens. Level 0 is the same hairline
 * grey as every border, so an empty day is a square rather than a gap.
 */
const heatBackgrounds: Record<HeatLevel, string> = {
  0: "bg-line",
  1: "bg-heat-1",
  2: "bg-heat-2",
  3: "bg-heat-3",
  4: "bg-heat-4",
};

type ContributionGraphProps = {
  /** One entry per day of the last year, oldest first. */
  contributions: ContributionDay[];
  /** GitHub's own total for the year, which may exceed the days shown. */
  totalContributions: number;
};

/**
 * The year's contribution graph: seven rows, one column per week.
 *
 * A server component, and deliberately not an interactive one — the figures are
 * in the HTML a crawler sees, with no JavaScript involved.
 *
 * Accessibility is the interesting part. 371 squares are 371 meaningless stops
 * for a screen reader, so the grid is a list of weeks: each column carries one
 * label ("Week of Nov 3, 2026: 12 contributions") and the squares inside it are
 * hidden. The year's total is stated as text above, so the headline figure
 * needs no graph reading at all.
 */
export function ContributionGraph({
  contributions,
  totalContributions,
}: ContributionGraphProps) {
  const weeks = toContributionWeeks(contributions);
  const busiest = busiestDay(contributions);

  return (
    <>
      <p className="mb-4.5 flex items-baseline gap-3">
        <b className="display text-[44px] normal-case">
          {totalContributions.toLocaleString("en-US")}
        </b>
        <span className="text-sm text-mute">contributions in the last year</span>
      </p>

      {/* Scrolls rather than squeezing: 53 columns do not fit a phone, and
          shrinking the squares would make the shape unreadable instead. */}
      <div className="overflow-x-auto pb-1.5">
        <ul
          role="list"
          className="flex w-max list-none gap-[3px]"
          aria-label={`Contributions per week over the last ${weeks.length} weeks`}
        >
          {weeks.map((week) => (
            <li
              key={week.startDate}
              className="grid grid-rows-7 gap-[3px]"
              aria-label={`Week of ${formatTimestampDate(week.startDate)}: ${
                week.total === 1 ? "1 contribution" : `${week.total} contributions`
              }`}
            >
              {week.days.map((day) => (
                <i
                  key={day.date}
                  aria-hidden="true"
                  className={`size-2.5 rounded-[2px] ${heatBackgrounds[
                    heatLevel(day.count, busiest)
                  ]}`}
                />
              ))}
            </li>
          ))}
        </ul>
      </div>
    </>
  );
}
