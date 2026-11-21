package dev.saq.portfolio.exception;

/**
 * GitHub answered, but not with data: a GraphQL {@code errors} array, or a response whose
 * {@code data} tree is missing the part that was asked for.
 *
 * <p>Separate from a transport failure on purpose. {@code RestClientException} means GitHub could
 * not be reached; this means it was reached and refused — an unknown login, a revoked token, an
 * exhausted rate limit. Both are handled the same way by
 * {@link dev.saq.portfolio.service.GithubService} (keep serving the last good value), but only
 * one of them is worth investigating, so they stay distinguishable in the logs.
 *
 * <p>Deliberately not mapped in {@code GlobalExceptionHandler}: it must never reach a response.
 * The service catches it and answers with cached data, or with
 * {@link UpstreamUnavailableException} when there is none.
 */
public class GithubApiException extends RuntimeException {

	public GithubApiException(String message) {
		super(message);
	}

	public GithubApiException(String message, Throwable cause) {
		super(message, cause);
	}

}
