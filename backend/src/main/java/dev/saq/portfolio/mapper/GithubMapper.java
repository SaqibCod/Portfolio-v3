package dev.saq.portfolio.mapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.saq.portfolio.dto.external.github.ContributionCalendarResponse;
import dev.saq.portfolio.dto.external.github.RecentActivityResponse;
import dev.saq.portfolio.dto.external.github.RepositoryLanguagesResponse;
import dev.saq.portfolio.dto.response.GithubActivityResponse;
import dev.saq.portfolio.dto.response.GithubSummaryResponse;
import dev.saq.portfolio.model.GithubActivityType;

import org.springframework.stereotype.Component;

/**
 * Turns GitHub's raw answers into the contract's shapes.
 *
 * <p>This is where the two pieces of real arithmetic live — summing language bytes across
 * repositories, and merging three kinds of activity into one ordered list — so they can be tested
 * without a network, a cache or a Spring context.
 */
@Component
public class GithubMapper {

	/** The contract caps {@code topLanguages} at six, which is also what the design shows. */
	static final int MAX_LANGUAGES = 6;

	/** One decimal place, as the design's legend renders it ("48.0%"). */
	private static final double PERCENTAGE_SCALE = 10d;

	/**
	 * The calendar and language figures as one summary.
	 *
	 * @param updatedAt when these figures were read from GitHub; passed in rather than read from
	 *                  the clock here, so a mapping is a pure function of its inputs
	 */
	public GithubSummaryResponse toSummary(String username,
			ContributionCalendarResponse.ContributionCalendar calendar,
			List<RepositoryLanguagesResponse.Repository> repositories,
			Instant updatedAt) {

		return new GithubSummaryResponse(username, calendar.totalContributions(),
				toContributionDays(calendar), toTopLanguages(repositories), updatedAt);
	}

	/**
	 * The calendar's weeks flattened into one day-per-entry list, oldest first.
	 *
	 * <p>GitHub nests days inside weeks because its own graph is drawn in columns. The contract
	 * publishes a flat list instead and lets the client decide on a layout, so the nesting is
	 * dropped here rather than being passed on.
	 */
	private static List<GithubSummaryResponse.ContributionDay> toContributionDays(
			ContributionCalendarResponse.ContributionCalendar calendar) {

		List<ContributionCalendarResponse.Week> weeks = calendar.weeks();
		if (weeks == null) {
			return List.of();
		}

		List<GithubSummaryResponse.ContributionDay> days = new ArrayList<>();
		for (ContributionCalendarResponse.Week week : weeks) {
			if (week == null || week.contributionDays() == null) {
				continue;
			}
			for (ContributionCalendarResponse.Day day : week.contributionDays()) {
				if (day != null && day.date() != null) {
					days.add(new GithubSummaryResponse.ContributionDay(day.date(),
							day.contributionCount()));
				}
			}
		}
		return List.copyOf(days);
	}

	/**
	 * The six biggest languages across every public repository, largest first.
	 *
	 * <p>Shares are weighted by bytes summed across repositories, which is the only measure GitHub
	 * publishes. Percentages are of the whole, not of the six shown: the six are a selection, and
	 * rescaling them to total 100% would overstate every one of them.
	 *
	 * <p>Ties are broken by name so that two languages with identical byte counts keep a stable
	 * order between refreshes instead of swapping places in the legend.
	 */
	private static List<GithubSummaryResponse.Language> toTopLanguages(
			List<RepositoryLanguagesResponse.Repository> repositories) {

		if (repositories == null || repositories.isEmpty()) {
			return List.of();
		}

		// Insertion-ordered so the tie-break below is the only thing deciding equal-size order,
		// rather than a hash order that could differ from one JVM run to the next.
		Map<String, Long> bytesByLanguage = new LinkedHashMap<>();
		Map<String, String> colorByLanguage = new LinkedHashMap<>();

		for (RepositoryLanguagesResponse.Repository repository : repositories) {
			if (repository == null || repository.languages() == null
					|| repository.languages().edges() == null) {
				continue;
			}
			for (RepositoryLanguagesResponse.LanguageEdge edge : repository.languages().edges()) {
				if (edge == null || edge.node() == null || edge.node().name() == null) {
					continue;
				}
				String name = edge.node().name();
				bytesByLanguage.merge(name, Math.max(edge.size(), 0), Long::sum);
				// First colour wins; GitHub publishes one colour per language, so any repository
				// reporting the language reports the same one.
				if (edge.node().color() != null) {
					colorByLanguage.putIfAbsent(name, edge.node().color());
				}
			}
		}

		long total = bytesByLanguage.values().stream().mapToLong(Long::longValue).sum();
		if (total == 0) {
			return List.of();
		}

		return bytesByLanguage.entrySet().stream()
				.sorted(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue)
						.reversed()
						.thenComparing(Map.Entry::getKey))
				.limit(MAX_LANGUAGES)
				.map(entry -> new GithubSummaryResponse.Language(entry.getKey(),
						percentage(entry.getValue(), total), colorByLanguage.get(entry.getKey())))
				.toList();
	}

	/** Bytes as a percentage of the whole, rounded to one decimal place. */
	private static double percentage(long bytes, long total) {
		double share = (double) bytes * 100 / total;
		return Math.round(share * PERCENTAGE_SCALE) / PERCENTAGE_SCALE;
	}

	/**
	 * Commits, pull requests and releases merged into one list, newest first.
	 *
	 * @param limit how many items to keep after merging. The merge happens across all three kinds
	 *              first, so the newest items win regardless of which kind they are
	 */
	public GithubActivityResponse toActivity(RecentActivityResponse.User user, int limit,
			Instant updatedAt) {

		List<GithubActivityResponse.Item> items = new ArrayList<>();
		collectCommitsAndReleases(user.repositories(), items);
		collectPullRequests(user.pullRequests(), items);

		List<GithubActivityResponse.Item> newestFirst = items.stream()
				.sorted(Comparator.comparing(GithubActivityResponse.Item::occurredAt).reversed())
				.limit(Math.max(limit, 0))
				.toList();

		return new GithubActivityResponse(newestFirst, updatedAt);
	}

	private static void collectCommitsAndReleases(RecentActivityResponse.Repositories repositories,
			List<GithubActivityResponse.Item> into) {

		if (repositories == null || repositories.nodes() == null) {
			return;
		}

		for (RecentActivityResponse.Repository repository : repositories.nodes()) {
			if (repository == null || repository.name() == null) {
				continue;
			}
			collectCommits(repository, into);
			collectReleases(repository, into);
		}
	}

	private static void collectCommits(RecentActivityResponse.Repository repository,
			List<GithubActivityResponse.Item> into) {

		// Null all the way down for a repository with no commits yet: GraphQL nulls
		// defaultBranchRef rather than returning an empty history.
		RecentActivityResponse.DefaultBranchRef branch = repository.defaultBranchRef();
		if (branch == null || branch.target() == null || branch.target().history() == null
				|| branch.target().history().nodes() == null) {
			return;
		}

		for (RecentActivityResponse.Commit commit : branch.target().history().nodes()) {
			if (commit == null || commit.committedDate() == null || commit.url() == null) {
				continue;
			}
			into.add(new GithubActivityResponse.Item(GithubActivityType.COMMIT, repository.name(),
					blankToNull(commit.messageHeadline()) == null
							? "Commit"
							: commit.messageHeadline().strip(),
					commit.url(), commit.committedDate()));
		}
	}

	private static void collectReleases(RecentActivityResponse.Repository repository,
			List<GithubActivityResponse.Item> into) {

		if (repository.releases() == null || repository.releases().nodes() == null) {
			return;
		}

		for (RecentActivityResponse.Release release : repository.releases().nodes()) {
			// A draft is invisible on GitHub itself, so publishing it here would leak something
			// unreleased. publishedAt is null for exactly those, which also leaves nothing to
			// order the list by.
			if (release == null || release.isDraft() || release.publishedAt() == null
					|| release.url() == null) {
				continue;
			}
			String title = blankToNull(release.name()) != null
					? release.name().strip()
					: blankToNull(release.tagName());
			if (title == null) {
				continue;
			}
			into.add(new GithubActivityResponse.Item(GithubActivityType.RELEASE, repository.name(),
					title, release.url(), release.publishedAt()));
		}
	}

	private static void collectPullRequests(RecentActivityResponse.PullRequests pullRequests,
			List<GithubActivityResponse.Item> into) {

		if (pullRequests == null || pullRequests.nodes() == null) {
			return;
		}

		for (RecentActivityResponse.PullRequest pullRequest : pullRequests.nodes()) {
			if (pullRequest == null || pullRequest.url() == null
					|| blankToNull(pullRequest.title()) == null) {
				continue;
			}
			RecentActivityResponse.PullRequestRepository repository = pullRequest.repository();
			// Private repositories are out of scope. A public-read token should never return one,
			// so this is a guard rather than a filter the feature depends on.
			if (repository == null || repository.name() == null || repository.isPrivate()) {
				continue;
			}
			// Merging is the moment worth reporting; an open pull request is dated from when it
			// was opened, which is the last thing that happened to it in public.
			Instant occurredAt = pullRequest.mergedAt() != null
					? pullRequest.mergedAt()
					: pullRequest.createdAt();
			if (occurredAt == null) {
				continue;
			}
			into.add(new GithubActivityResponse.Item(GithubActivityType.PULL_REQUEST,
					repository.name(), pullRequest.title().strip(), pullRequest.url(), occurredAt));
		}
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

}
