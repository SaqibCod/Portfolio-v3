package dev.saq.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;

import dev.saq.portfolio.client.GithubClient;
import dev.saq.portfolio.config.GithubProperties;
import dev.saq.portfolio.dto.response.GithubActivityResponse;
import dev.saq.portfolio.dto.response.GithubSummaryResponse;
import dev.saq.portfolio.exception.UpstreamUnavailableException;
import dev.saq.portfolio.mapper.GithubMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * What the service does when GitHub stops answering, which is the whole point of it.
 *
 * <p>No Spring context here, so the {@code @Cacheable} annotations are inert: every call below
 * reaches GitHub. That is deliberate — it isolates the last-good-value behaviour from the cache,
 * and it is the only way to observe what a cache miss on a bad day actually returns. The cache
 * itself is exercised end to end in {@code GithubIntegrationTests}.
 */
class GithubServiceTest {

	/** Not {@code GithubClient.GRAPHQL_URL}: that constant is package-private to the client. */
	private static final String GRAPHQL_URL = "https://api.github.com/graphql";

	private static final String CALENDAR_JSON = """
			{"data":{"user":{"contributionsCollection":{"contributionCalendar":{
			  "totalContributions":1284,
			  "weeks":[{"contributionDays":[{"date":"2026-11-20","contributionCount":4}]}]}}}}}""";

	private static final String LANGUAGES_JSON = """
			{"data":{"user":{"repositories":{"nodes":[
			  {"name":"githmind","languages":{"edges":[
			    {"size":480,"node":{"name":"Java","color":"#b07219"}},
			    {"size":320,"node":{"name":"TypeScript","color":"#3178c6"}}]}}]}}}}""";

	private static final String ACTIVITY_JSON = """
			{"data":{"user":{
			  "repositories":{"nodes":[{"name":"githmind",
			    "defaultBranchRef":{"target":{"history":{"nodes":[
			      {"messageHeadline":"Add streaming responses","committedDate":"2026-11-21T06:00:00Z",
			       "url":"https://github.com/test-user/githmind/commit/abc"}]}}},
			    "releases":{"nodes":[]}}]},
			  "pullRequests":{"nodes":[
			    {"title":"Fall back to Ollama","url":"https://github.com/test-user/medi-scan/pull/12",
			     "createdAt":"2026-11-19T12:00:00Z","mergedAt":"2026-11-20T08:00:00Z",
			     "repository":{"name":"medi-scan","isPrivate":false}}]}}}}""";

	private MockRestServiceServer github;

	private GithubService githubService;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		github = MockRestServiceServer.bindTo(builder).build();
		githubService = new GithubService(
				new GithubClient(builder, new GithubProperties("test-github-token", "test-user")),
				new GithubMapper());
	}

	// --- The good path ------------------------------------------------------------------------

	@Test
	void buildsASummaryFromGithub() {
		expectGithubUp();

		GithubSummaryResponse summary = githubService.getSummary();

		assertThat(summary.username()).isEqualTo("test-user");
		assertThat(summary.totalContributions()).isEqualTo(1284);
		assertThat(summary.contributions()).hasSize(1);
		assertThat(summary.topLanguages()).extracting(GithubSummaryResponse.Language::name)
				.containsExactly("Java", "TypeScript");
		assertThat(summary.updatedAt()).isNotNull();
	}

	@Test
	void buildsAnActivityListFromGithub() {
		expectGithubUp();

		GithubActivityResponse activity = githubService.getActivity(5);

		assertThat(activity.items()).extracting(GithubActivityResponse.Item::title)
				.containsExactly("Add streaming responses", "Fall back to Ollama");
	}

	@Test
	void returnsOnlyAsManyItemsAsWereAskedFor() {
		expectGithubUp();

		GithubActivityResponse activity = githubService.getActivity(1);

		assertThat(activity.items()).hasSize(1);
		assertThat(activity.items().getFirst().title()).isEqualTo("Add streaming responses");
	}

	@Test
	void stampsTheSummaryWithWhenItWasFetched() {
		// The staleness tests below rest on this: updatedAt is the fetch time, so a value served
		// from the last good fetch keeps saying when that fetch happened.
		Instant before = Instant.now();
		expectGithubUp();

		assertThat(githubService.getSummary().updatedAt()).isAfterOrEqualTo(before);
	}

	// --- Stale while error --------------------------------------------------------------------

	@Test
	void keepsServingTheLastGoodSummaryWhenGithubFails() {
		expectGithubUp();
		GithubSummaryResponse good = githubService.getSummary();

		github.reset();
		expectGithubDown();

		GithubSummaryResponse served = githubService.getSummary();

		// The same figures, and critically the same updatedAt: a reader is told how old the data
		// is rather than that it was refreshed a moment ago.
		assertThat(served).isEqualTo(good);
	}

	@Test
	void keepsServingTheLastGoodActivityWhenGithubFails() {
		expectGithubUp();
		GithubActivityResponse good = githubService.getActivity(5);

		github.reset();
		expectGithubDown();

		assertThat(githubService.getActivity(5)).isEqualTo(good);
	}

	@Test
	void servesAnyLimitFromTheLastGoodActivity() {
		// The full twenty items are always fetched, so a limit nobody asked for while GitHub was
		// up can still be answered while it is down.
		expectGithubUp();
		githubService.getActivity(1);

		github.reset();
		expectGithubDown();

		GithubActivityResponse served = githubService.getActivity(10);

		assertThat(served.items()).extracting(GithubActivityResponse.Item::title)
				.containsExactly("Add streaming responses", "Fall back to Ollama");
	}

	@Test
	void keepsServingTheLastGoodValueWhenGithubRefusesRatherThanFails() {
		// A GraphQL errors array inside a 200 — a revoked token, an exhausted rate limit — has to
		// be treated exactly like an unreachable GitHub.
		expectGithubUp();
		GithubSummaryResponse good = githubService.getSummary();

		github.reset();
		github.expect(manyTimes(), requestTo(GRAPHQL_URL)).andRespond(withSuccess("""
				{"data":{"user":null},"errors":[{"type":"FORBIDDEN","message":"Bad credentials"}]}""",
				MediaType.APPLICATION_JSON));

		assertThat(githubService.getSummary()).isEqualTo(good);
	}

	@Test
	void doesNotLetRepeatedFailuresDiscardTheLastGoodValue() {
		expectGithubUp();
		GithubSummaryResponse good = githubService.getSummary();

		github.reset();
		expectGithubDown();
		githubService.getSummary();
		githubService.getSummary();

		assertThat(githubService.getSummary()).isEqualTo(good);
	}

	@Test
	void replacesTheLastGoodValueOnceGithubRecovers() {
		expectGithubUp();
		GithubSummaryResponse first = githubService.getSummary();

		github.reset();
		expectGithubDown();
		githubService.getSummary();

		github.reset();
		expectGithubUp(CALENDAR_JSON.replace("1284", "1300"));

		GithubSummaryResponse recovered = githubService.getSummary();

		assertThat(recovered.totalContributions()).isEqualTo(1300);
		assertThat(recovered.updatedAt()).isAfterOrEqualTo(first.updatedAt());
	}

	// --- Nothing to serve ---------------------------------------------------------------------

	@Test
	void answers503WhenGithubFailsAndNothingWasEverFetched() {
		expectGithubDown();

		assertThatThrownBy(() -> githubService.getSummary())
				.isInstanceOf(UpstreamUnavailableException.class)
				.hasMessageContaining("temporarily unavailable");
	}

	@Test
	void answers503ForActivityWhenNothingWasEverFetched() {
		expectGithubDown();

		assertThatThrownBy(() -> githubService.getActivity(5))
				.isInstanceOf(UpstreamUnavailableException.class)
				.asInstanceOf(
						org.assertj.core.api.InstanceOfAssertFactories.type(
								UpstreamUnavailableException.class))
				.returns(UpstreamUnavailableException.DEFAULT_RETRY_AFTER_SECONDS,
						UpstreamUnavailableException::getRetryAfterSeconds);
	}

	@Test
	void keepsSummaryAndActivityIndependent() {
		// One endpoint failing must not take the other down: they are separate fetches with
		// separate last good values.
		expectGithubUp();
		GithubActivityResponse activity = githubService.getActivity(5);

		github.reset();
		expectGithubDown();

		assertThatThrownBy(() -> githubService.getSummary())
				.isInstanceOf(UpstreamUnavailableException.class);
		assertThat(githubService.getActivity(5)).isEqualTo(activity);
	}

	// --- Fixtures -----------------------------------------------------------------------------

	private void expectGithubUp() {
		expectGithubUp(CALENDAR_JSON);
	}

	/**
	 * A working GitHub, for any number of calls.
	 *
	 * <p>All three queries are POSTs to the same URL, so a stub cannot be selected by request
	 * matcher — this one reads the query document and answers accordingly, which also keeps the
	 * tests independent of the order the service fetches in.
	 */
	private void expectGithubUp(String calendarJson) {
		github.expect(manyTimes(), requestTo(GRAPHQL_URL)).andRespond(request -> {
			String query = ((MockClientHttpRequest) request).getBodyAsString();
			String json;
			if (query.contains("contributionCalendar")) {
				json = calendarJson;
			}
			else if (query.contains("messageHeadline")) {
				json = ACTIVITY_JSON;
			}
			else {
				json = LANGUAGES_JSON;
			}
			return withSuccess(json, MediaType.APPLICATION_JSON).createResponse(request);
		});
	}

	/** A GitHub that cannot be reached at all. */
	private void expectGithubDown() {
		github.expect(manyTimes(), requestTo(GRAPHQL_URL)).andRespond(withServerError());
	}

}
