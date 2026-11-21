package dev.saq.portfolio.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import dev.saq.portfolio.dto.response.GithubActivityResponse;
import dev.saq.portfolio.dto.response.GithubSummaryResponse;
import dev.saq.portfolio.exception.UpstreamUnavailableException;
import dev.saq.portfolio.model.GithubActivityType;
import dev.saq.portfolio.model.RateLimitPolicy;
import dev.saq.portfolio.service.GithubService;
import dev.saq.testsupport.WebInfrastructureTestConfig;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * HTTP in, HTTP out: the two JSON shapes the contract promises, the cache headers, the
 * {@code limit} bounds and the 503, with the service mocked.
 *
 * <p>Every request presents its own remote address. The rate-limit interceptor is live in this
 * slice and buckets are keyed by hashed IP, so distinct addresses keep the tests independent
 * despite the shared application context.
 */
@WebMvcTest(GithubController.class)
@Import(WebInfrastructureTestConfig.class)
@ActiveProfiles("test")
class GithubControllerTest {

	private static final String PROBLEM_BASE = "https://api.example.com/problems/";

	private static final String SUMMARY_PATH = "/api/v1/github/summary";

	private static final String ACTIVITY_PATH = "/api/v1/github/activity";

	private static final Instant UPDATED_AT = Instant.parse("2026-11-21T08:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private GithubService githubService;

	// --- Summary ------------------------------------------------------------------------------

	@Test
	void servesTheSummaryInTheContractsShape() throws Exception {
		given(githubService.getSummary()).willReturn(new GithubSummaryResponse("SaqibCod", 1284,
				List.of(new GithubSummaryResponse.ContributionDay(LocalDate.of(2026, 11, 20), 4)),
				List.of(new GithubSummaryResponse.Language("Java", 48.0, "#b07219")), UPDATED_AT));

		mockMvc.perform(get("203.0.114.1", SUMMARY_PATH))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", MediaType.APPLICATION_JSON_VALUE))
				.andExpect(jsonPath("$.username").value("SaqibCod"))
				.andExpect(jsonPath("$.totalContributions").value(1284))
				.andExpect(jsonPath("$.contributions[0].date").value("2026-11-20"))
				.andExpect(jsonPath("$.contributions[0].count").value(4))
				.andExpect(jsonPath("$.topLanguages[0].name").value("Java"))
				.andExpect(jsonPath("$.topLanguages[0].percentage").value(48.0))
				.andExpect(jsonPath("$.topLanguages[0].color").value("#b07219"))
				.andExpect(jsonPath("$.updatedAt").value("2026-11-21T08:00:00Z"));
	}

	@Test
	void letsTheSummaryBeCachedPublicly() throws Exception {
		given(githubService.getSummary()).willReturn(summary());

		// A browser or CDN may reuse this; nothing in it is per-visitor.
		mockMvc.perform(get("203.0.114.2", SUMMARY_PATH))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "max-age=3600, public"));
	}

	@Test
	void answersTheSummaryWith503WhenGithubHasNeverBeenReached() throws Exception {
		willThrow(new UpstreamUnavailableException("GitHub data is temporarily unavailable."))
				.given(githubService).getSummary();

		mockMvc.perform(get("203.0.114.3", SUMMARY_PATH))
				.andExpect(status().isServiceUnavailable())
				.andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
				.andExpect(header().exists("Retry-After"))
				.andExpect(jsonPath("$.type").value(PROBLEM_BASE + "upstream-unavailable"))
				.andExpect(jsonPath("$.status").value(503));
	}

	// --- Activity -----------------------------------------------------------------------------

	@Test
	void servesActivityInTheContractsShape() throws Exception {
		given(githubService.getActivity(anyInt())).willReturn(new GithubActivityResponse(
				List.of(new GithubActivityResponse.Item(GithubActivityType.PULL_REQUEST,
						"medi-scan", "Fall back to Ollama when Gemini times out",
						"https://github.com/SaqibCod/medi-scan/pull/12",
						Instant.parse("2026-11-20T08:00:00Z"))),
				UPDATED_AT));

		mockMvc.perform(get("203.0.114.4", ACTIVITY_PATH))
				.andExpect(status().isOk())
				// The contract's enum spelling, not the Java constant's.
				.andExpect(jsonPath("$.items[0].type").value("pull_request"))
				.andExpect(jsonPath("$.items[0].repo").value("medi-scan"))
				.andExpect(jsonPath("$.items[0].title")
						.value("Fall back to Ollama when Gemini times out"))
				.andExpect(jsonPath("$.items[0].url")
						.value("https://github.com/SaqibCod/medi-scan/pull/12"))
				.andExpect(jsonPath("$.items[0].occurredAt").value("2026-11-20T08:00:00Z"))
				.andExpect(jsonPath("$.updatedAt").value("2026-11-21T08:00:00Z"));
	}

	@Test
	void spellsEveryActivityTypeTheWayTheContractDoes() throws Exception {
		given(githubService.getActivity(anyInt())).willReturn(new GithubActivityResponse(
				List.of(item(GithubActivityType.COMMIT), item(GithubActivityType.PULL_REQUEST),
						item(GithubActivityType.RELEASE)),
				UPDATED_AT));

		mockMvc.perform(get("203.0.114.5", ACTIVITY_PATH))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].type").value("commit"))
				.andExpect(jsonPath("$.items[1].type").value("pull_request"))
				.andExpect(jsonPath("$.items[2].type").value("release"));
	}

	@Test
	void letsActivityBeCachedPubliclyForOneRefreshPeriod() throws Exception {
		given(githubService.getActivity(anyInt())).willReturn(activity());

		mockMvc.perform(get("203.0.114.6", ACTIVITY_PATH))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "max-age=900, public"));
	}

	@Test
	void defaultsToFiveItems() throws Exception {
		given(githubService.getActivity(anyInt())).willReturn(activity());

		mockMvc.perform(get("203.0.114.7", ACTIVITY_PATH)).andExpect(status().isOk());

		then(githubService).should().getActivity(5);
	}

	@Test
	void defaultsToTheSizeTheRefreshJobKeepsWarm() {
		// The activity cache is keyed by limit, so a default that drifted away from the primed
		// size would send every visitor's first post-refresh request to GitHub.
		assertThat(GithubController.DEFAULT_LIMIT)
				.isEqualTo(Integer.toString(GithubService.DEFAULT_ACTIVITY_ITEMS));
	}

	@Test
	void passesTheRequestedLimitStraightThrough() throws Exception {
		given(githubService.getActivity(anyInt())).willReturn(activity());

		mockMvc.perform(get("203.0.114.8", ACTIVITY_PATH + "?limit=4"))
				.andExpect(status().isOk());

		then(githubService).should().getActivity(4);
	}

	@Test
	void rejectsALimitBelowOne() throws Exception {
		mockMvc.perform(get("203.0.114.9", ACTIVITY_PATH + "?limit=0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value(PROBLEM_BASE + "validation"));

		then(githubService).should(never()).getActivity(anyInt());
	}

	@Test
	void rejectsALimitAboveTwenty() throws Exception {
		mockMvc.perform(get("203.0.114.10", ACTIVITY_PATH + "?limit=21"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value(PROBLEM_BASE + "validation"));

		then(githubService).should(never()).getActivity(anyInt());
	}

	@Test
	void rejectsALimitThatIsNotANumber() throws Exception {
		mockMvc.perform(get("203.0.114.11", ACTIVITY_PATH + "?limit=lots"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value(PROBLEM_BASE + "validation"));
	}

	@Test
	void answersActivityWith503WhenGithubHasNeverBeenReached() throws Exception {
		willThrow(new UpstreamUnavailableException("GitHub data is temporarily unavailable."))
				.given(githubService).getActivity(anyInt());

		mockMvc.perform(get("203.0.114.12", ACTIVITY_PATH))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.type").value(PROBLEM_BASE + "upstream-unavailable"));
	}

	// --- Rate limiting ------------------------------------------------------------------------

	@Test
	void carriesTheReadRateLimitHeaders() throws Exception {
		given(githubService.getSummary()).willReturn(summary());

		mockMvc.perform(get("203.0.114.13", SUMMARY_PATH))
				.andExpect(status().isOk())
				.andExpect(header().string("RateLimit-Limit",
						Long.toString(RateLimitPolicy.PUBLIC_READ.limit())))
				.andExpect(header().string("RateLimit-Remaining",
						Long.toString(RateLimitPolicy.PUBLIC_READ.limit() - 1)));
	}

	// --- Fixtures -----------------------------------------------------------------------------

	private static GithubSummaryResponse summary() {
		return new GithubSummaryResponse("SaqibCod", 1284, List.of(), List.of(), UPDATED_AT);
	}

	private static GithubActivityResponse activity() {
		return new GithubActivityResponse(List.of(), UPDATED_AT);
	}

	private static GithubActivityResponse.Item item(GithubActivityType type) {
		return new GithubActivityResponse.Item(type, "portfolio", "Something happened",
				"https://github.com/SaqibCod/portfolio", UPDATED_AT);
	}

	/** A GET that appears to come from {@code ip}, so it gets its own rate-limit bucket. */
	private static MockHttpServletRequestBuilder get(String ip, String uri) {
		return MockMvcRequestBuilders.get(uri).with(fromAddress(ip));
	}

	private static RequestPostProcessor fromAddress(String ip) {
		return request -> {
			request.setRemoteAddr(ip);
			return request;
		};
	}

}
