package dev.saq.portfolio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * Which GitHub account the site reports on, and the token it is read with.
 *
 * <p>Separate from {@link AppProperties} because this is one feature's configuration rather than
 * shared infrastructure, and because a missing GitHub token should read as "the GitHub feature is
 * not configured" rather than as a hole in the application's core settings.
 *
 * <p>Both values are required. Only public data is ever requested, but the token is not optional:
 * GitHub's GraphQL API refuses unauthenticated requests outright, and every figure on the site —
 * the contribution calendar above all — comes from GraphQL.
 *
 * @param token    a GitHub personal access token. Needs no scopes beyond public read; a
 *                 fine-grained token with "Public repositories (read-only)" is enough
 * @param username the account whose public activity is published on the site
 */
@Validated
@ConfigurationProperties(prefix = "app.github")
public record GithubProperties(
		@NotBlank String token,
		@NotBlank String username) {
}
