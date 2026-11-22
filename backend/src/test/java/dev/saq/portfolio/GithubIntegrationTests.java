package dev.saq.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import dev.saq.portfolio.client.GithubClient;
import dev.saq.portfolio.dto.external.github.ContributionCalendarResponse;
import dev.saq.portfolio.dto.external.github.RecentActivityResponse;
import dev.saq.portfolio.dto.external.github.RepositoryLanguagesResponse;
import dev.saq.portfolio.exception.GithubApiException;
import dev.saq.portfolio.service.GithubService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.client.ResourceAccessException;

/**
 * Both GitHub endpoints end to end: a real servlet container, the real cache, and the status
 * codes, JSON shapes and headers the contract promises.
 *
 * <p>{@link GithubClient} is the one seam replaced. Everything above it is real — the cache, the
 * last-good-value logic, the mapper, the controller — and GitHub itself is never called. Mocking
 * at the client is also what makes the central claim measurable: the number of calls the mock
 * receives is exactly the number of times GitHub would have been contacted.
 *
 * <p>The caches are cleared before each test rather than between assertions, because a warm entry
 * from the previous test would make the next one's first request look like a cache hit.
 */
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GithubIntegrationTests {

	private static final String PROBLEM_BASE = "https://api.example.com/problems/";

	private static final String SUMMARY_PATH = "/api/v1/github/summary";

	private static final String ACTIVITY_PATH = "/api/v1/github/activity";

	@MockitoBean
	private GithubClient githubClient;

	@Autowired
	private GithubService githubService;

	@Autowired
	private CacheManager cacheManager;

	@LocalServerPort
	private int port;

	private RestTestClient client;

	@BeforeEach
	void setUp() {
		client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
		cacheManager.getCacheNames()
				.forEach(name -> cacheManager.getCache(name).clear());
	}

	// --- Contract shapes ----------------------------------------------------------------------

	@Test
	void servesTheSummaryInTheContractsShape() {
		givenGithubIsUp();

		client.get().uri(SUMMARY_PATH)
				.exchange()
				.expectStatus().isOk()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
				.expectHeader().valueEquals("Cache-Control", "max-age=3600, public")
				.expectBody()
				.jsonPath("$.username").isEqualTo("test-user")
				.jsonPath("$.totalContributions").isEqualTo(1284)
				.jsonPath("$.contributions.length()").isEqualTo(2)
				.jsonPath("$.contributions[0].date").isEqualTo("2026-11-19")
				.jsonPath("$.contributions[0].count").isEqualTo(4)
				.jsonPath("$.topLanguages[0].name").isEqualTo("Java")
				.jsonPath("$.topLanguages[0].percentage").isEqualTo(60.0)
				.jsonPath("$.topLanguages[0].color").isEqualTo("#b07219")
				.jsonPath("$.topLanguages[1].name").isEqualTo("TypeScript")
				.jsonPath("$.updatedAt").exists();
	}

	@Test
	void servesActivityInTheContractsShape() {
		givenGithubIsUp();

		client.get().uri(ACTIVITY_PATH)
				.exchange()
				.expectStatus().isOk()
				.expectHeader().valueEquals("Cache-Control", "max-age=900, public")
				.expectBody()
				.jsonPath("$.items[0].type").isEqualTo("commit")
				.jsonPath("$.items[0].repo").isEqualTo("githmind")
				.jsonPath("$.items[0].title").isEqualTo("Add streaming responses")
				.jsonPath("$.items[0].url")
				.isEqualTo("https://github.com/test-user/githmind/commit/abc")
				.jsonPath("$.items[0].occurredAt").isEqualTo("2026-11-21T06:00:00Z")
				.jsonPath("$.items[1].type").isEqualTo("pull_request")
				.jsonPath("$.items[1].repo").isEqualTo("medi-scan")
				.jsonPath("$.updatedAt").exists();
	}

	@Test
	void returnsEverythingAvailableByDefaultAndHonoursASmallerLimit() {
		givenGithubIsUp();

		client.get().uri(ACTIVITY_PATH).exchange()
				.expectStatus().isOk()
				.expectBody().jsonPath("$.items.length()").isEqualTo(2);

		client.get().uri(ACTIVITY_PATH + "?limit=1").exchange()
				.expectStatus().isOk()
				.expectBody().jsonPath("$.items.length()").isEqualTo(1);
	}

	@Test
	void rejectsALimitOutsideTheContractsRange() {
		client.get().uri(ACTIVITY_PATH + "?limit=21")
				.exchange()
				.expectStatus().isBadRequest()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.type").isEqualTo(PROBLEM_BASE + "validation");
	}

	// --- The cache ----------------------------------------------------------------------------

	@Test
	void repeatedSummaryRequestsDoNotReachGithub() {
		givenGithubIsUp();

		for (int request = 0; request < 5; request++) {
			client.get().uri(SUMMARY_PATH).exchange().expectStatus().isOk();
		}

		// The acceptance criterion: one fetch, five answers.
		then(githubClient).should(times(1)).fetchContributionCalendar();
		then(githubClient).should(times(1)).fetchLanguageBytes();
	}

	@Test
	void repeatedActivityRequestsDoNotReachGithub() {
		givenGithubIsUp();

		for (int request = 0; request < 5; request++) {
			client.get().uri(ACTIVITY_PATH).exchange().expectStatus().isOk();
		}

		then(githubClient).should(times(1)).fetchRecentActivity();
	}

	@Test
	void doesNotReachGithubAfterTheRefreshJobHasPrimedTheDefault() {
		givenGithubIsUp();

		// What the scheduler does on its own interval, which the test profile keeps switched off.
		githubService.evictActivity();
		githubService.getActivity(GithubService.DEFAULT_ACTIVITY_ITEMS);

		client.get().uri(ACTIVITY_PATH).exchange().expectStatus().isOk();

		// The point of priming: a visitor arriving after a refresh is served from the cache, so
		// the default request size and the primed size have to be the same number.
		then(githubClient).should(times(1)).fetchRecentActivity();
	}

	@Test
	void reachesGithubAgainOnceTheCachedEntryIsGone() {
		givenGithubIsUp();
		client.get().uri(SUMMARY_PATH).exchange().expectStatus().isOk();

		// Clearing stands in for the six-hour expiry, which no test can wait out.
		cacheManager.getCache("githubSummary").clear();
		client.get().uri(SUMMARY_PATH).exchange().expectStatus().isOk();

		then(githubClient).should(times(2)).fetchContributionCalendar();
	}

	// --- Stale while error --------------------------------------------------------------------

	@Test
	void keepsServingTheLastGoodSummaryAfterTheTokenGoesBad() {
		givenGithubIsUp();
		String freshUpdatedAt = updatedAtOf(SUMMARY_PATH);

		// What a revoked or expired token looks like: GitHub answers, but with errors.
		cacheManager.getCache("githubSummary").clear();
		willThrow(new GithubApiException("GitHub reported 1 GraphQL error(s): FORBIDDEN: Bad credentials"))
				.given(githubClient).fetchContributionCalendar();

		client.get().uri(SUMMARY_PATH)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.totalContributions").isEqualTo(1284)
				// Still the original timestamp: the figures are old and say so.
				.jsonPath("$.updatedAt").isEqualTo(freshUpdatedAt);
	}

	@Test
	void keepsServingTheLastGoodActivityWhenGithubCannotBeReached() {
		givenGithubIsUp();
		String freshUpdatedAt = updatedAtOf(ACTIVITY_PATH);

		cacheManager.getCache("githubActivity").clear();
		willThrow(new ResourceAccessException("Connection reset"))
				.given(githubClient).fetchRecentActivity();

		client.get().uri(ACTIVITY_PATH)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.items.length()").isEqualTo(2)
				.jsonPath("$.updatedAt").isEqualTo(freshUpdatedAt);
	}

	@Test
	void servesAFreshlyAskedForLimitFromTheLastGoodActivity() {
		// A limit that was never cached, requested while GitHub is down. The last good value
		// holds the full list, so it can still be answered.
		givenGithubIsUp();
		client.get().uri(ACTIVITY_PATH + "?limit=1").exchange().expectStatus().isOk();

		willThrow(new ResourceAccessException("Connection reset"))
				.given(githubClient).fetchRecentActivity();

		client.get().uri(ACTIVITY_PATH + "?limit=2")
				.exchange()
				.expectStatus().isOk()
				.expectBody().jsonPath("$.items.length()").isEqualTo(2);
	}

	// --- Nothing to serve ---------------------------------------------------------------------

	/**
	 * The 503 path needs a service that has never succeeded, and the last good value lives in a
	 * singleton that outlives one test. A fresh context is the only honest way to get there.
	 */
	@Test
	@DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
	void answersTheSummaryWith503WhenGithubHasNeverBeenReached() {
		willThrow(new ResourceAccessException("Connection reset"))
				.given(githubClient).fetchContributionCalendar();

		client.get().uri(SUMMARY_PATH)
				.exchange()
				.expectStatus().isEqualTo(503)
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectHeader().exists("Retry-After")
				.expectBody()
				.jsonPath("$.type").isEqualTo(PROBLEM_BASE + "upstream-unavailable")
				.jsonPath("$.title").isEqualTo("Service unavailable")
				.jsonPath("$.status").isEqualTo(503)
				// Never a stack trace, and never GitHub's own wording.
				.jsonPath("$.detail").isEqualTo("GitHub data is temporarily unavailable. Try again shortly.");
	}

	@Test
	@DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
	void answersActivityWith503WhenGithubHasNeverBeenReached() {
		willThrow(new GithubApiException("GitHub returned no recent activity"))
				.given(githubClient).fetchRecentActivity();

		client.get().uri(ACTIVITY_PATH)
				.exchange()
				.expectStatus().isEqualTo(503)
				.expectBody()
				.jsonPath("$.type").isEqualTo(PROBLEM_BASE + "upstream-unavailable");
	}

	// --- Fixtures -----------------------------------------------------------------------------

	private void givenGithubIsUp() {
		given(githubClient.username()).willReturn("test-user");
		given(githubClient.fetchContributionCalendar()).willReturn(
				new ContributionCalendarResponse.ContributionCalendar(1284,
						List.of(new ContributionCalendarResponse.Week(List.of(
								new ContributionCalendarResponse.Day(
										LocalDate.of(2026, 11, 19), 4),
								new ContributionCalendarResponse.Day(
										LocalDate.of(2026, 11, 20), 0))))));
		given(githubClient.fetchLanguageBytes()).willReturn(List.of(
				new RepositoryLanguagesResponse.Repository("githmind",
						new RepositoryLanguagesResponse.Languages(List.of(
								new RepositoryLanguagesResponse.LanguageEdge(600,
										new RepositoryLanguagesResponse.Language("Java",
												"#b07219")),
								new RepositoryLanguagesResponse.LanguageEdge(400,
										new RepositoryLanguagesResponse.Language("TypeScript",
												"#3178c6")))))));
		given(githubClient.fetchRecentActivity()).willReturn(new RecentActivityResponse.User(
				new RecentActivityResponse.Repositories(List.of(
						new RecentActivityResponse.Repository("githmind",
								new RecentActivityResponse.DefaultBranchRef(
										new RecentActivityResponse.Target(
												new RecentActivityResponse.History(List.of(
														new RecentActivityResponse.Commit(
																"Add streaming responses",
																Instant.parse(
																		"2026-11-21T06:00:00Z"),
																"https://github.com/test-user/githmind/commit/abc"))))),
								new RecentActivityResponse.Releases(List.of())))),
				new RecentActivityResponse.PullRequests(List.of(
						new RecentActivityResponse.PullRequest("Fall back to Ollama",
								"https://github.com/test-user/medi-scan/pull/12",
								Instant.parse("2026-11-19T12:00:00Z"),
								Instant.parse("2026-11-20T08:00:00Z"),
								new RecentActivityResponse.PullRequestRepository("medi-scan",
										false))))));
	}

	/** The {@code updatedAt} a fresh fetch stamps, so a later response can be compared to it. */
	private String updatedAtOf(String path) {
		byte[] body = client.get().uri(path)
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.returnResult()
				.getResponseBody();

		String json = new String(body);
		int start = json.indexOf("\"updatedAt\":\"") + "\"updatedAt\":\"".length();
		String updatedAt = json.substring(start, json.indexOf('"', start));
		assertThat(updatedAt).isNotBlank();
		return updatedAt;
	}

}
