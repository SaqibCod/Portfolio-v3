package dev.saq.portfolio.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import dev.saq.portfolio.config.GithubProperties;
import dev.saq.portfolio.dto.external.github.ContributionCalendarResponse;
import dev.saq.portfolio.dto.external.github.RecentActivityResponse;
import dev.saq.portfolio.dto.external.github.RepositoryLanguagesResponse;
import dev.saq.portfolio.exception.GithubApiException;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * The three GraphQL calls against a stubbed GitHub: what is sent, what is parsed back, and what
 * happens when GitHub answers {@code 200 OK} with an {@code errors} array instead of data.
 */
class GithubClientTest {

	private static final String TOKEN = "test-github-token";

	private static final String USERNAME = "test-user";

	private MockRestServiceServer github;

	private GithubClient githubClient;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		github = MockRestServiceServer.bindTo(builder).build();
		githubClient = new GithubClient(builder, new GithubProperties(TOKEN, USERNAME));
	}

	// --- Contribution calendar ----------------------------------------------------------------

	@Test
	void readsTheContributionCalendar() {
		github.expect(requestTo(GithubClient.GRAPHQL_URL))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess("""
						{
						  "data": {
						    "user": {
						      "contributionsCollection": {
						        "contributionCalendar": {
						          "totalContributions": 1284,
						          "weeks": [
						            { "contributionDays": [
						                { "date": "2026-11-15", "contributionCount": 4 },
						                { "date": "2026-11-16", "contributionCount": 0 }
						            ] }
						          ]
						        }
						      }
						    }
						  }
						}""", MediaType.APPLICATION_JSON));

		ContributionCalendarResponse.ContributionCalendar calendar =
				githubClient.fetchContributionCalendar();

		assertThat(calendar.totalContributions()).isEqualTo(1284);
		assertThat(calendar.weeks()).hasSize(1);
		assertThat(calendar.weeks().getFirst().contributionDays())
				.containsExactly(
						new ContributionCalendarResponse.Day(LocalDate.of(2026, 11, 15), 4),
						new ContributionCalendarResponse.Day(LocalDate.of(2026, 11, 16), 0));
		github.verify();
	}

	@Test
	void authenticatesEveryCallAndPassesTheLoginAsAVariable() {
		github.expect(requestTo(GithubClient.GRAPHQL_URL))
				.andExpect(header("Authorization", "Bearer " + TOKEN))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				// A variable, never interpolated into the document: a login cannot then be read
				// as query syntax.
				.andExpect(jsonPath("$.variables.login").value(USERNAME))
				.andExpect(jsonPath("$.query",
						Matchers.containsString("contributionCalendar")))
				.andRespond(withSuccess("""
						{"data":{"user":{"contributionsCollection":{"contributionCalendar":
						{"totalContributions":0,"weeks":[]}}}}}""", MediaType.APPLICATION_JSON));

		githubClient.fetchContributionCalendar();

		github.verify();
	}

	// --- Languages ----------------------------------------------------------------------------

	@Test
	void readsLanguageBytesPerRepository() {
		github.expect(requestTo(GithubClient.GRAPHQL_URL))
				.andExpect(jsonPath("$.query", Matchers.allOf(
						// Only this account's own public, non-fork repositories.
						Matchers.containsString("ownerAffiliations: [OWNER]"),
						Matchers.containsString("privacy: PUBLIC"),
						Matchers.containsString("isFork: false"))))
				.andRespond(withSuccess("""
						{
						  "data": {
						    "user": {
						      "repositories": {
						        "nodes": [
						          {
						            "name": "githmind",
						            "languages": { "edges": [
						              { "size": 48000, "node": { "name": "Java", "color": "#b07219" } },
						              { "size": 31000, "node": { "name": "TypeScript", "color": "#3178c6" } }
						            ] }
						          }
						        ]
						      }
						    }
						  }
						}""", MediaType.APPLICATION_JSON));

		List<RepositoryLanguagesResponse.Repository> repositories =
				githubClient.fetchLanguageBytes();

		assertThat(repositories).hasSize(1);
		assertThat(repositories.getFirst().name()).isEqualTo("githmind");
		assertThat(repositories.getFirst().languages().edges()).hasSize(2);
		assertThat(repositories.getFirst().languages().edges().getFirst().size()).isEqualTo(48000);
		assertThat(repositories.getFirst().languages().edges().getFirst().node().color())
				.isEqualTo("#b07219");
		github.verify();
	}

	// --- Recent activity ----------------------------------------------------------------------

	@Test
	void readsCommitsReleasesAndPullRequests() {
		github.expect(requestTo(GithubClient.GRAPHQL_URL))
				.andExpect(jsonPath("$.query", Matchers.allOf(
						// The fields the Events API no longer carries, which is why this query
						// exists at all.
						Matchers.containsString("messageHeadline"),
						Matchers.containsString("pullRequests"),
						Matchers.containsString("releases"))))
				.andRespond(withSuccess("""
						{
						  "data": {
						    "user": {
						      "repositories": { "nodes": [
						        {
						          "name": "githmind",
						          "defaultBranchRef": { "target": { "history": { "nodes": [
						            {
						              "messageHeadline": "Add streaming responses to chat endpoint",
						              "committedDate": "2026-11-21T06:00:00Z",
						              "url": "https://github.com/test-user/githmind/commit/abc"
						            }
						          ] } } },
						          "releases": { "nodes": [
						            {
						              "name": "v1.1.0",
						              "tagName": "v1.1.0",
						              "url": "https://github.com/test-user/githmind/releases/tag/v1.1.0",
						              "publishedAt": "2026-11-18T09:00:00Z",
						              "isDraft": false
						            }
						          ] }
						        }
						      ] },
						      "pullRequests": { "nodes": [
						        {
						          "title": "Fall back to Ollama when Gemini times out",
						          "url": "https://github.com/test-user/medi-scan/pull/12",
						          "createdAt": "2026-11-19T12:00:00Z",
						          "mergedAt": "2026-11-20T08:00:00Z",
						          "repository": { "name": "medi-scan", "isPrivate": false }
						        }
						      ] }
						    }
						  }
						}""", MediaType.APPLICATION_JSON));

		RecentActivityResponse.User user = githubClient.fetchRecentActivity();

		RecentActivityResponse.Repository repository = user.repositories().nodes().getFirst();
		assertThat(repository.defaultBranchRef().target().history().nodes().getFirst())
				.returns("Add streaming responses to chat endpoint",
						RecentActivityResponse.Commit::messageHeadline)
				.returns(Instant.parse("2026-11-21T06:00:00Z"),
						RecentActivityResponse.Commit::committedDate);
		assertThat(repository.releases().nodes().getFirst().isDraft()).isFalse();
		assertThat(repository.releases().nodes().getFirst().publishedAt())
				.isEqualTo(Instant.parse("2026-11-18T09:00:00Z"));

		RecentActivityResponse.PullRequest pullRequest = user.pullRequests().nodes().getFirst();
		assertThat(pullRequest.title()).isEqualTo("Fall back to Ollama when Gemini times out");
		assertThat(pullRequest.mergedAt()).isEqualTo(Instant.parse("2026-11-20T08:00:00Z"));
		assertThat(pullRequest.repository().isPrivate()).isFalse();
		github.verify();
	}

	@Test
	void readsADraftReleaseAsADraft() {
		// isDraft and isPrivate are the two booleans whose JSON names start with "is". Asserted
		// explicitly because a mapping that silently read them as false would publish drafts and
		// private repositories.
		github.expect(requestTo(GithubClient.GRAPHQL_URL)).andRespond(withSuccess("""
				{"data":{"user":{
				  "repositories":{"nodes":[{"name":"portfolio","defaultBranchRef":null,
				    "releases":{"nodes":[{"name":"v2.0.0","tagName":"v2.0.0",
				      "url":"https://github.com/test-user/portfolio/releases/tag/v2.0.0",
				      "publishedAt":null,"isDraft":true}]}}]},
				  "pullRequests":{"nodes":[{"title":"Private work","url":"https://example.com/pr/1",
				    "createdAt":"2026-11-19T12:00:00Z","mergedAt":null,
				    "repository":{"name":"secret","isPrivate":true}}]}}}}""",
				MediaType.APPLICATION_JSON));

		RecentActivityResponse.User user = githubClient.fetchRecentActivity();

		assertThat(user.repositories().nodes().getFirst().releases().nodes().getFirst().isDraft())
				.isTrue();
		assertThat(user.pullRequests().nodes().getFirst().repository().isPrivate()).isTrue();
	}

	// --- Failures -----------------------------------------------------------------------------

	@Test
	void treatsAGraphQlErrorArrayAsAFailureDespiteThe200() {
		github.expect(requestTo(GithubClient.GRAPHQL_URL)).andRespond(withSuccess("""
				{
				  "data": { "user": null },
				  "errors": [
				    { "type": "NOT_FOUND", "message": "Could not resolve to a User with the login of 'test-user'." }
				  ]
				}""", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> githubClient.fetchContributionCalendar())
				.isInstanceOf(GithubApiException.class)
				.hasMessageContaining("NOT_FOUND")
				.hasMessageContaining("Could not resolve to a User");
	}

	@Test
	void treatsAMissingSubtreeAsAFailureRatherThanEmptyData() {
		// GraphQL nulls a whole branch instead of omitting one field, so "no user" has to be a
		// failure: mapping it as an empty calendar would publish zero contributions as fact.
		github.expect(requestTo(GithubClient.GRAPHQL_URL))
				.andRespond(withSuccess("{\"data\":{\"user\":null}}", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> githubClient.fetchContributionCalendar())
				.isInstanceOf(GithubApiException.class)
				.hasMessageContaining("contribution calendar");
	}

	@Test
	void letsATransportFailureSurfaceAsIs() {
		// Not wrapped: the service distinguishes "GitHub refused" from "GitHub unreachable", and
		// only the first is worth investigating.
		github.expect(requestTo(GithubClient.GRAPHQL_URL)).andRespond(withServerError());

		assertThatThrownBy(() -> githubClient.fetchLanguageBytes())
				.isInstanceOf(RestClientException.class);
	}

	@Test
	void reportsTheConfiguredUsername() {
		assertThat(githubClient.username()).isEqualTo(USERNAME);
	}

}
