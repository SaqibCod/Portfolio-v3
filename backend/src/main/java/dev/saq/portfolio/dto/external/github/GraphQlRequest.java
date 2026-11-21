package dev.saq.portfolio.dto.external.github;

import java.util.Map;

/**
 * The body of a GitHub GraphQL call: a query document and its variables.
 *
 * <p>Variables rather than string interpolation, so a username can never be read as query
 * syntax.
 */
public record GraphQlRequest(String query, Map<String, Object> variables) {
}
