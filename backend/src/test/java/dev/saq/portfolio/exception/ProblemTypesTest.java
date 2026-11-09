package dev.saq.portfolio.exception;

import static org.assertj.core.api.Assertions.assertThat;

import dev.saq.testsupport.TestAppProperties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The base comes from {@code app.api.base-url}; the {@code /problems/{slug}} structure and the
 * slugs themselves are fixed, because clients match on the full URI.
 */
class ProblemTypesTest {

	@Test
	void buildsEveryTypeFromTheConfiguredBase() {
		ProblemTypes types = new ProblemTypes("https://api.example.com");

		assertThat(types.validation()).hasToString("https://api.example.com/problems/validation");
		assertThat(types.unauthorized()).hasToString("https://api.example.com/problems/unauthorized");
		assertThat(types.notFound()).hasToString("https://api.example.com/problems/not-found");
		assertThat(types.turnstileFailed()).hasToString("https://api.example.com/problems/turnstile-failed");
		assertThat(types.rateLimited()).hasToString("https://api.example.com/problems/rate-limited");
		assertThat(types.upstreamUnavailable())
				.hasToString("https://api.example.com/problems/upstream-unavailable");
		assertThat(types.internal()).hasToString("https://api.example.com/problems/internal");
	}

	@Test
	void followsADifferentBaseWithoutChangingThePathStructure() {
		ProblemTypes types = new ProblemTypes("https://api.saq.dev");

		assertThat(types.validation()).hasToString("https://api.saq.dev/problems/validation");
		assertThat(types.rateLimited()).hasToString("https://api.saq.dev/problems/rate-limited");
		assertThat(types.internal()).hasToString("https://api.saq.dev/problems/internal");
	}

	@ParameterizedTest
	@ValueSource(strings = { "https://api.example.com/", "  https://api.example.com  ",
			"https://api.example.com" })
	void toleratesTrailingSlashesAndSurroundingSpace(String baseUrl) {
		assertThat(new ProblemTypes(baseUrl).validation())
				.hasToString("https://api.example.com/problems/validation");
	}

	@Test
	void readsTheBaseFromApplicationProperties() {
		ProblemTypes types = new ProblemTypes(TestAppProperties.sample());

		assertThat(types.notFound())
				.hasToString(TestAppProperties.API_BASE_URL + "/problems/not-found");
	}

	@Test
	void everyTypeIsAnAbsoluteUri() {
		ProblemTypes types = new ProblemTypes(TestAppProperties.sample());

		assertThat(types.validation().isAbsolute()).isTrue();
		assertThat(types.unauthorized().isAbsolute()).isTrue();
		assertThat(types.notFound().isAbsolute()).isTrue();
		assertThat(types.turnstileFailed().isAbsolute()).isTrue();
		assertThat(types.rateLimited().isAbsolute()).isTrue();
		assertThat(types.upstreamUnavailable().isAbsolute()).isTrue();
		assertThat(types.internal().isAbsolute()).isTrue();
	}

}
