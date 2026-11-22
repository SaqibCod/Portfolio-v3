package dev.saq.portfolio.dto.external.github;

/**
 * One entry from a GraphQL response's {@code errors} array.
 *
 * <p>GraphQL reports a bad query, a missing user or a revoked token as {@code errors} inside a
 * {@code 200 OK}, so a response is only usable once this list is known to be empty —
 * {@link dev.saq.portfolio.client.GithubClient} checks it on every call.
 *
 * @param type    GitHub's machine-readable category, e.g. {@code NOT_FOUND}; absent on some errors
 * @param message the human-readable explanation
 */
public record GraphQlError(String type, String message) {
}
