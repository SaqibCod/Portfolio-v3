package dev.saq.portfolio.client;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import dev.saq.portfolio.config.GithubProperties;
import dev.saq.portfolio.dto.external.github.ContributionCalendarResponse;
import dev.saq.portfolio.dto.external.github.GraphQlError;
import dev.saq.portfolio.dto.external.github.GraphQlRequest;
import dev.saq.portfolio.dto.external.github.RecentActivityResponse;
import dev.saq.portfolio.dto.external.github.RepositoryLanguagesResponse;
import dev.saq.portfolio.exception.GithubApiException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Thin wrapper over GitHub's API, reading only public data for one account.
 *
 * <p>Transport only: it issues the query and hands back GitHub's answer. What the numbers mean,
 * and what to do when they cannot be fetched, belong to
 * {@link dev.saq.portfolio.service.GithubService}.
 *
 * <p>Everything goes through GraphQL. The contribution calendar has no REST equivalent, language
 * byte counts would be one REST call per repository, and recent activity can no longer be read
 * from {@code /users/{u}/events/public} at all — GitHub trimmed those payloads on 7 October 2025
 * and they no longer contain commit messages or pull request titles. One POST per concern is both
 * fewer round trips and the only way left to get the titles the design shows.
 */
@Component
public class GithubClient {

	static final String GRAPHQL_URL = "https://api.github.com/graphql";

	/**
	 * Owned, public, non-fork repositories, most recently pushed first.
	 *
	 * <p>{@code ownerAffiliations: [OWNER]} drops repositories the account only collaborates on
	 * (the default includes {@code COLLABORATOR}), and {@code privacy: PUBLIC} is belt and braces
	 * next to a public-read-only token.
	 */
	private static final String OWNED_PUBLIC_REPOS = """
			repositories(
			  first: $repos
			  ownerAffiliations: [OWNER]
			  privacy: PUBLIC
			  isFork: false
			  orderBy: { field: PUSHED_AT, direction: DESC }
			)""";

	private static final String CONTRIBUTION_CALENDAR_QUERY = """
			query ContributionCalendar($login: String!) {
			  user(login: $login) {
			    contributionsCollection {
			      contributionCalendar {
			        totalContributions
			        weeks {
			          contributionDays {
			            date
			            contributionCount
			          }
			        }
			      }
			    }
			  }
			}
			""";

	private static final String LANGUAGE_BYTES_QUERY = """
			query LanguageBytes($login: String!, $repos: Int!, $languages: Int!) {
			  user(login: $login) {
			    %s {
			      nodes {
			        name
			        languages(first: $languages, orderBy: { field: SIZE, direction: DESC }) {
			          edges {
			            size
			            node {
			              name
			              color
			            }
			          }
			        }
			      }
			    }
			  }
			}
			""".formatted(OWNED_PUBLIC_REPOS);

	/**
	 * Commits and releases from the most recently pushed repositories, plus pull requests from
	 * anywhere.
	 *
	 * <p>Pull requests are queried on the user rather than per repository, so work on other
	 * people's projects still appears. {@code states: [OPEN, MERGED]} leaves out closed-unmerged
	 * ones, which are not something to advertise as "currently building".
	 */
	private static final String RECENT_ACTIVITY_QUERY = """
			query RecentActivity(
			  $login: String!
			  $repos: Int!
			  $commits: Int!
			  $releases: Int!
			  $pulls: Int!
			) {
			  user(login: $login) {
			    %s {
			      nodes {
			        name
			        defaultBranchRef {
			          target {
			            ... on Commit {
			              history(first: $commits) {
			                nodes {
			                  messageHeadline
			                  committedDate
			                  url
			                }
			              }
			            }
			          }
			        }
			        releases(first: $releases, orderBy: { field: CREATED_AT, direction: DESC }) {
			          nodes {
			            name
			            tagName
			            url
			            publishedAt
			            isDraft
			          }
			        }
			      }
			    }
			    pullRequests(
			      first: $pulls
			      states: [OPEN, MERGED]
			      orderBy: { field: UPDATED_AT, direction: DESC }
			    ) {
			      nodes {
			        title
			        url
			        createdAt
			        mergedAt
			        repository {
			          name
			          isPrivate
			        }
			      }
			    }
			  }
			}
			""".formatted(OWNED_PUBLIC_REPOS);

	/** How many repositories to read languages from. GitHub's page ceiling is 100. */
	private static final int LANGUAGE_REPOS = 100;

	/** Languages per repository. More than this in one repository is noise at six on screen. */
	private static final int LANGUAGES_PER_REPO = 10;

	/**
	 * How many repositories to scan for commits and releases. Small on purpose: the list shows a
	 * handful of items, and "recently pushed" means anything older cannot reach the top anyway.
	 */
	private static final int ACTIVITY_REPOS = 6;

	private static final int COMMITS_PER_REPO = 5;

	private static final int RELEASES_PER_REPO = 2;

	private static final int PULL_REQUESTS = 10;

	private final RestClient restClient;

	private final String username;

	public GithubClient(RestClient.Builder restClientBuilder, GithubProperties properties) {
		this.restClient = restClientBuilder
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.token())
				.defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
				.build();
		this.username = properties.username();
	}

	/** The account these figures describe, so callers need no second copy of the name. */
	public String username() {
		return username;
	}

	/**
	 * The last year's contribution calendar.
	 *
	 * @throws GithubApiException                                 if GitHub reports a GraphQL error
	 *                                                            or returns no calendar
	 * @throws org.springframework.web.client.RestClientException if GitHub cannot be reached
	 */
	public ContributionCalendarResponse.ContributionCalendar fetchContributionCalendar() {
		ContributionCalendarResponse response = post(CONTRIBUTION_CALENDAR_QUERY,
				Map.of("login", username), ContributionCalendarResponse.class,
				ContributionCalendarResponse::errors);

		return require(
				navigate(response, r -> r.data().user().contributionsCollection().contributionCalendar()),
				"contribution calendar");
	}

	/**
	 * Bytes per language for every owned public non-fork repository, unaggregated.
	 *
	 * @throws GithubApiException                                 if GitHub reports a GraphQL error
	 *                                                            or returns no repository list
	 * @throws org.springframework.web.client.RestClientException if GitHub cannot be reached
	 */
	public List<RepositoryLanguagesResponse.Repository> fetchLanguageBytes() {
		RepositoryLanguagesResponse response = post(LANGUAGE_BYTES_QUERY,
				Map.of("login", username, "repos", LANGUAGE_REPOS, "languages", LANGUAGES_PER_REPO),
				RepositoryLanguagesResponse.class, RepositoryLanguagesResponse::errors);

		return require(navigate(response, r -> r.data().user().repositories().nodes()),
				"repository languages");
	}

	/**
	 * Recent commits, pull requests and releases, still separated by kind.
	 *
	 * @throws GithubApiException                                 if GitHub reports a GraphQL error
	 *                                                            or returns no user
	 * @throws org.springframework.web.client.RestClientException if GitHub cannot be reached
	 */
	public RecentActivityResponse.User fetchRecentActivity() {
		RecentActivityResponse response = post(RECENT_ACTIVITY_QUERY,
				Map.of("login", username,
						"repos", ACTIVITY_REPOS,
						"commits", COMMITS_PER_REPO,
						"releases", RELEASES_PER_REPO,
						"pulls", PULL_REQUESTS),
				RecentActivityResponse.class, RecentActivityResponse::errors);

		return require(navigate(response, r -> r.data().user()), "recent activity");
	}

	/**
	 * One GraphQL round trip, with the {@code errors} array checked before the body is used.
	 *
	 * <p>GraphQL answers a bad query, an unknown login or a revoked token with {@code 200 OK} and
	 * an {@code errors} array, so a successful status says nothing on its own. Checking here means
	 * no caller can forget to.
	 */
	private <T> T post(String query, Map<String, Object> variables, Class<T> responseType,
			Function<T, List<GraphQlError>> errors) {

		T response = restClient.post()
				.uri(GRAPHQL_URL)
				.contentType(MediaType.APPLICATION_JSON)
				.body(new GraphQlRequest(query, variables))
				.retrieve()
				.body(responseType);

		if (response == null) {
			throw new GithubApiException("GitHub returned an empty body");
		}
		List<GraphQlError> reported = errors.apply(response);
		if (reported != null && !reported.isEmpty()) {
			throw new GithubApiException("GitHub reported " + reported.size()
					+ " GraphQL error(s): " + describe(reported));
		}
		return response;
	}

	/**
	 * Reads a value out of a response tree, treating any missing level as simply absent.
	 *
	 * <p>GraphQL nulls a whole subtree rather than omitting one field, so every level of these
	 * records is nullable and a chain of accessors would be a wall of null checks. The
	 * {@link NullPointerException} being caught here is the expected signal, not a bug: it means
	 * GitHub did not return that branch.
	 */
	private static <T, R> R navigate(T response, Function<T, R> path) {
		try {
			return path.apply(response);
		}
		catch (NullPointerException ex) {
			return null;
		}
	}

	private static <T> T require(T value, String what) {
		if (value == null) {
			throw new GithubApiException("GitHub returned no " + what);
		}
		return value;
	}

	/** Messages only — a GraphQL error body can be large and holds nothing secret worth keeping. */
	private static String describe(List<GraphQlError> errors) {
		return errors.stream()
				.map(error -> error.type() == null
						? String.valueOf(error.message())
						: error.type() + ": " + error.message())
				.reduce((first, second) -> first + "; " + second)
				.orElse("");
	}

}
