package dev.saq.portfolio.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import dev.saq.portfolio.dto.external.github.ContributionCalendarResponse;
import dev.saq.portfolio.dto.external.github.RecentActivityResponse;
import dev.saq.portfolio.dto.external.github.RepositoryLanguagesResponse;
import dev.saq.portfolio.dto.response.GithubActivityResponse;
import dev.saq.portfolio.dto.response.GithubSummaryResponse;
import dev.saq.portfolio.model.GithubActivityType;

import org.junit.jupiter.api.Test;

/** The arithmetic: flattening the calendar, summing language bytes, and merging activity. */
class GithubMapperTest {

	private static final Instant UPDATED_AT = Instant.parse("2026-11-21T08:00:00Z");

	private final GithubMapper mapper = new GithubMapper();

	// --- Summary ------------------------------------------------------------------------------

	@Test
	void flattensTheCalendarIntoOneEntryPerDay() {
		ContributionCalendarResponse.ContributionCalendar calendar = calendar(1284,
				week(day("2026-11-15", 4), day("2026-11-16", 0)),
				week(day("2026-11-17", 7)));

		GithubSummaryResponse summary = mapper.toSummary("test-user", calendar, List.of(),
				UPDATED_AT);

		assertThat(summary.username()).isEqualTo("test-user");
		assertThat(summary.totalContributions()).isEqualTo(1284);
		assertThat(summary.updatedAt()).isEqualTo(UPDATED_AT);
		// Oldest first, zero days kept: the graph needs a square for every day of the year.
		assertThat(summary.contributions()).containsExactly(
				new GithubSummaryResponse.ContributionDay(LocalDate.of(2026, 11, 15), 4),
				new GithubSummaryResponse.ContributionDay(LocalDate.of(2026, 11, 16), 0),
				new GithubSummaryResponse.ContributionDay(LocalDate.of(2026, 11, 17), 7));
	}

	@Test
	void reportsTheTotalGithubGivesRatherThanRecomputingIt() {
		// GitHub's total covers the full year including private contributions the calendar's
		// days do not itemise, so re-summing the days would quietly report a smaller number.
		ContributionCalendarResponse.ContributionCalendar calendar = calendar(900,
				week(day("2026-11-15", 1)));

		GithubSummaryResponse summary = mapper.toSummary("test-user", calendar, List.of(),
				UPDATED_AT);

		assertThat(summary.totalContributions()).isEqualTo(900);
		assertThat(summary.contributions()).hasSize(1);
	}

	@Test
	void survivesACalendarWithNoWeeks() {
		GithubSummaryResponse summary = mapper.toSummary("test-user", calendar(0), List.of(),
				UPDATED_AT);

		assertThat(summary.contributions()).isEmpty();
		assertThat(summary.topLanguages()).isEmpty();
	}

	// --- Languages ----------------------------------------------------------------------------

	@Test
	void sumsLanguageBytesAcrossEveryRepository() {
		List<RepositoryLanguagesResponse.Repository> repositories = List.of(
				repository("githmind", language("Java", "#b07219", 60), language("CSS", null, 10)),
				repository("portfolio", language("Java", "#b07219", 20),
						language("TypeScript", "#3178c6", 10)));

		GithubSummaryResponse summary = mapper.toSummary("test-user", calendar(0), repositories,
				UPDATED_AT);

		// Java is 60 + 20 bytes across the two repositories. CSS and TypeScript tie at 10, so
		// the name tie-break decides their order — see ordersEqualSizedLanguagesByName.
		assertThat(summary.topLanguages()).containsExactly(
				new GithubSummaryResponse.Language("Java", 80.0, "#b07219"),
				new GithubSummaryResponse.Language("CSS", 10.0, null),
				new GithubSummaryResponse.Language("TypeScript", 10.0, "#3178c6"));
	}

	@Test
	void keepsOnlyTheSixLargestLanguages() {
		List<RepositoryLanguagesResponse.Repository> repositories = List.of(repository("all",
				language("Java", "#b07219", 800),
				language("TypeScript", "#3178c6", 400),
				language("Python", "#3572A5", 200),
				language("CSS", "#663399", 100),
				language("Go", "#00ADD8", 50),
				language("Rust", "#dea584", 25),
				language("Shell", "#89e051", 12),
				language("Dockerfile", "#384d54", 6)));

		GithubSummaryResponse summary = mapper.toSummary("test-user", calendar(0), repositories,
				UPDATED_AT);

		assertThat(summary.topLanguages()).hasSize(GithubMapper.MAX_LANGUAGES);
		assertThat(summary.topLanguages()).extracting(GithubSummaryResponse.Language::name)
				.containsExactly("Java", "TypeScript", "Python", "CSS", "Go", "Rust");
	}

	@Test
	void statesEachShareOfTheWholeRatherThanOfTheSixShown() {
		// 1593 bytes in total; the six shown cover 1575 of them. Rescaling the six to sum to
		// 100% would overstate every one of them, so the percentages deliberately do not.
		List<RepositoryLanguagesResponse.Repository> repositories = List.of(repository("all",
				language("Java", "#b07219", 800),
				language("TypeScript", "#3178c6", 400),
				language("Python", "#3572A5", 200),
				language("CSS", "#663399", 100),
				language("Go", "#00ADD8", 50),
				language("Rust", "#dea584", 25),
				language("Shell", "#89e051", 18)));

		GithubSummaryResponse summary = mapper.toSummary("test-user", calendar(0), repositories,
				UPDATED_AT);

		assertThat(summary.topLanguages()).extracting(GithubSummaryResponse.Language::percentage)
				.containsExactly(50.2, 25.1, 12.6, 6.3, 3.1, 1.6);
		double shown = summary.topLanguages().stream()
				.mapToDouble(GithubSummaryResponse.Language::percentage)
				.sum();
		assertThat(shown).isLessThan(100.0);
	}

	@Test
	void roundsPercentagesToOneDecimalPlace() {
		// 1 of 3 bytes is 33.333…%, which has to reach the legend as "33.3".
		List<RepositoryLanguagesResponse.Repository> repositories = List.of(repository("thirds",
				language("Java", "#b07219", 1), language("Go", "#00ADD8", 2)));

		GithubSummaryResponse summary = mapper.toSummary("test-user", calendar(0), repositories,
				UPDATED_AT);

		assertThat(summary.topLanguages()).extracting(GithubSummaryResponse.Language::percentage)
				.containsExactly(66.7, 33.3);
	}

	@Test
	void ordersEqualSizedLanguagesByNameSoTheLegendDoesNotShuffle() {
		List<RepositoryLanguagesResponse.Repository> repositories = List.of(repository("tied",
				language("TypeScript", "#3178c6", 100), language("Java", "#b07219", 100)));

		GithubSummaryResponse summary = mapper.toSummary("test-user", calendar(0), repositories,
				UPDATED_AT);

		assertThat(summary.topLanguages()).extracting(GithubSummaryResponse.Language::name)
				.containsExactly("Java", "TypeScript");
	}

	@Test
	void reportsNoLanguagesWhenEveryRepositoryIsEmpty() {
		List<RepositoryLanguagesResponse.Repository> repositories = List.of(
				new RepositoryLanguagesResponse.Repository("empty", null),
				repository("zero", language("Java", "#b07219", 0)));

		GithubSummaryResponse summary = mapper.toSummary("test-user", calendar(0), repositories,
				UPDATED_AT);

		// Zero bytes in total: a percentage of nothing is not 0%, it is unknown.
		assertThat(summary.topLanguages()).isEmpty();
	}

	// --- Activity -----------------------------------------------------------------------------

	@Test
	void mergesCommitsPullRequestsAndReleasesNewestFirst() {
		RecentActivityResponse.User user = new RecentActivityResponse.User(
				repositories(
						activityRepository("githmind",
								commits(commit("Add streaming responses", "2026-11-21T06:00:00Z")),
								releases(release("v1.1.0", "v1.1.0", "2026-11-18T09:00:00Z",
										false))),
						activityRepository("portfolio",
								commits(commit("Cursor-based pagination", "2026-11-17T10:00:00Z")),
								releases())),
				pullRequests(pullRequest("Fall back to Ollama", "medi-scan", false,
						"2026-11-19T12:00:00Z", "2026-11-20T08:00:00Z")));

		GithubActivityResponse activity = mapper.toActivity(user, 10, UPDATED_AT);

		assertThat(activity.updatedAt()).isEqualTo(UPDATED_AT);
		assertThat(activity.items()).extracting(GithubActivityResponse.Item::title)
				.containsExactly("Add streaming responses", "Fall back to Ollama", "v1.1.0",
						"Cursor-based pagination");
		assertThat(activity.items()).extracting(GithubActivityResponse.Item::type)
				.containsExactly(GithubActivityType.COMMIT, GithubActivityType.PULL_REQUEST,
						GithubActivityType.RELEASE, GithubActivityType.COMMIT);
		assertThat(activity.items()).extracting(GithubActivityResponse.Item::repo)
				.containsExactly("githmind", "medi-scan", "githmind", "portfolio");
	}

	@Test
	void appliesTheLimitAfterMergingSoTheNewestItemsWinWhateverTheirKind() {
		RecentActivityResponse.User user = new RecentActivityResponse.User(
				repositories(activityRepository("portfolio",
						commits(commit("Older commit", "2026-11-01T10:00:00Z")),
						releases())),
				pullRequests(pullRequest("Newer pull request", "portfolio", false,
						"2026-11-20T10:00:00Z", null)));

		GithubActivityResponse activity = mapper.toActivity(user, 1, UPDATED_AT);

		assertThat(activity.items()).extracting(GithubActivityResponse.Item::title)
				.containsExactly("Newer pull request");
	}

	@Test
	void datesAMergedPullRequestFromItsMergeAndAnOpenOneFromItsOpening() {
		RecentActivityResponse.User user = new RecentActivityResponse.User(repositories(),
				pullRequests(
						pullRequest("Merged work", "githmind", false, "2026-11-10T10:00:00Z",
								"2026-11-12T10:00:00Z"),
						pullRequest("Open work", "githmind", false, "2026-11-11T10:00:00Z", null)));

		GithubActivityResponse activity = mapper.toActivity(user, 10, UPDATED_AT);

		assertThat(activity.items()).extracting(GithubActivityResponse.Item::occurredAt)
				.containsExactly(Instant.parse("2026-11-12T10:00:00Z"),
						Instant.parse("2026-11-11T10:00:00Z"));
	}

	@Test
	void leavesOutDraftReleases() {
		RecentActivityResponse.User user = new RecentActivityResponse.User(
				repositories(activityRepository("portfolio", commits(),
						releases(release("v2.0.0", "v2.0.0", null, true),
								release("v1.9.0", "v1.9.0", "2026-11-01T09:00:00Z", false)))),
				pullRequests());

		GithubActivityResponse activity = mapper.toActivity(user, 10, UPDATED_AT);

		// A draft is invisible on GitHub itself; publishing it here would announce something
		// unreleased.
		assertThat(activity.items()).extracting(GithubActivityResponse.Item::title)
				.containsExactly("v1.9.0");
	}

	@Test
	void leavesOutPullRequestsInPrivateRepositories() {
		RecentActivityResponse.User user = new RecentActivityResponse.User(repositories(),
				pullRequests(
						pullRequest("Private work", "client-project", true, "2026-11-19T12:00:00Z",
								null),
						pullRequest("Public work", "githmind", false, "2026-11-18T12:00:00Z",
								null)));

		GithubActivityResponse activity = mapper.toActivity(user, 10, UPDATED_AT);

		assertThat(activity.items()).extracting(GithubActivityResponse.Item::title)
				.containsExactly("Public work");
	}

	@Test
	void fallsBackToTheTagWhenAReleaseHasNoTitle() {
		RecentActivityResponse.User user = new RecentActivityResponse.User(
				repositories(activityRepository("portfolio", commits(),
						releases(release("", "v1.2.0", "2026-11-01T09:00:00Z", false)))),
				pullRequests());

		GithubActivityResponse activity = mapper.toActivity(user, 10, UPDATED_AT);

		assertThat(activity.items()).extracting(GithubActivityResponse.Item::title)
				.containsExactly("v1.2.0");
	}

	@Test
	void survivesARepositoryWithNoCommitsAtAll() {
		// GraphQL nulls defaultBranchRef for an empty repository rather than returning an empty
		// history, so every level has to be treated as absent.
		RecentActivityResponse.User user = new RecentActivityResponse.User(
				repositories(new RecentActivityResponse.Repository("fresh", null, null)),
				pullRequests());

		GithubActivityResponse activity = mapper.toActivity(user, 10, UPDATED_AT);

		assertThat(activity.items()).isEmpty();
	}

	// --- Fixtures -----------------------------------------------------------------------------

	private static ContributionCalendarResponse.ContributionCalendar calendar(int total,
			ContributionCalendarResponse.Week... weeks) {

		return new ContributionCalendarResponse.ContributionCalendar(total,
				weeks.length == 0 ? null : List.of(weeks));
	}

	private static ContributionCalendarResponse.Week week(
			ContributionCalendarResponse.Day... days) {

		return new ContributionCalendarResponse.Week(List.of(days));
	}

	private static ContributionCalendarResponse.Day day(String date, int count) {
		return new ContributionCalendarResponse.Day(LocalDate.parse(date), count);
	}

	private static RepositoryLanguagesResponse.Repository repository(String name,
			RepositoryLanguagesResponse.LanguageEdge... edges) {

		return new RepositoryLanguagesResponse.Repository(name,
				new RepositoryLanguagesResponse.Languages(List.of(edges)));
	}

	private static RepositoryLanguagesResponse.LanguageEdge language(String name, String color,
			long size) {

		return new RepositoryLanguagesResponse.LanguageEdge(size,
				new RepositoryLanguagesResponse.Language(name, color));
	}

	private static RecentActivityResponse.Repositories repositories(
			RecentActivityResponse.Repository... nodes) {

		return new RecentActivityResponse.Repositories(List.of(nodes));
	}

	private static RecentActivityResponse.Repository activityRepository(String name,
			RecentActivityResponse.History history, RecentActivityResponse.Releases releases) {

		return new RecentActivityResponse.Repository(name,
				new RecentActivityResponse.DefaultBranchRef(
						new RecentActivityResponse.Target(history)),
				releases);
	}

	private static RecentActivityResponse.History commits(
			RecentActivityResponse.Commit... commits) {

		return new RecentActivityResponse.History(List.of(commits));
	}

	private static RecentActivityResponse.Commit commit(String headline, String committedDate) {
		return new RecentActivityResponse.Commit(headline, Instant.parse(committedDate),
				"https://github.com/test-user/repo/commit/" + headline.hashCode());
	}

	private static RecentActivityResponse.Releases releases(
			RecentActivityResponse.Release... releases) {

		return new RecentActivityResponse.Releases(List.of(releases));
	}

	private static RecentActivityResponse.Release release(String name, String tagName,
			String publishedAt, boolean draft) {

		return new RecentActivityResponse.Release(name, tagName,
				"https://github.com/test-user/repo/releases/tag/" + tagName,
				publishedAt == null ? null : Instant.parse(publishedAt), draft);
	}

	private static RecentActivityResponse.PullRequests pullRequests(
			RecentActivityResponse.PullRequest... pullRequests) {

		return new RecentActivityResponse.PullRequests(List.of(pullRequests));
	}

	private static RecentActivityResponse.PullRequest pullRequest(String title, String repo,
			boolean privateRepo, String createdAt, String mergedAt) {

		return new RecentActivityResponse.PullRequest(title,
				"https://github.com/test-user/" + repo + "/pull/1", Instant.parse(createdAt),
				mergedAt == null ? null : Instant.parse(mergedAt),
				new RecentActivityResponse.PullRequestRepository(repo, privateRepo));
	}

}
