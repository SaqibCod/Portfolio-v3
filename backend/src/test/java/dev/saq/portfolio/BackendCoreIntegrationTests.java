package dev.saq.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import dev.saq.portfolio.model.RateLimitPolicy;
import dev.saq.testsupport.TestAppProperties;
import dev.saq.testsupport.TestEndpointsConfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * End-to-end checks over a real servlet container and a real Postgres, so the infrastructure is
 * verified as a client sees it: status codes, {@code application/problem+json} bodies and headers.
 */
@Import({ TestcontainersConfig.class, TestEndpointsConfig.class })
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BackendCoreIntegrationTests {

	private static final String PROBLEM_BASE = "https://api.example.com/problems/";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@LocalServerPort
	private int port;

	private RestTestClient client;

	@BeforeEach
	void bindClient() {
		client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
	}

	@Test
	void baselineMigrationInstalledTheExtensions() {
		assertThat(extensionExists("vector")).isTrue();
		assertThat(extensionExists("pgcrypto")).isTrue();
	}

	@Test
	void genRandomUuidIsAvailable() {
		assertThat(jdbcTemplate.queryForObject("SELECT gen_random_uuid()", String.class)).isNotBlank();
	}

	@Test
	void errorsAreProblemJson() {
		client.get().uri("/api/v1/test/not-found")
				.exchange()
				.expectStatus().isNotFound()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.type").isEqualTo(PROBLEM_BASE + "not-found")
				.jsonPath("$.title").isEqualTo("Not found")
				.jsonPath("$.status").isEqualTo(404);
	}

	@Test
	void validationFailureListsTheOffendingFields() {
		client.post().uri("/api/v1/test/validate")
				.contentType(MediaType.APPLICATION_JSON)
				.body("""
						{"name": "", "message": "far too long to fit"}""")
				.exchange()
				.expectStatus().isBadRequest()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.type").isEqualTo(PROBLEM_BASE + "validation")
				.jsonPath("$.title").isEqualTo("Validation failed")
				.jsonPath("$.errors.length()").isEqualTo(2)
				.jsonPath("$.errors[?(@.field == 'name')]").exists()
				.jsonPath("$.errors[?(@.field == 'message')]").exists();
	}

	@Test
	void turnstileFailureIsUnprocessableContent() {
		client.get().uri("/api/v1/test/turnstile-failed")
				.exchange()
				.expectStatus().isEqualTo(422)
				.expectBody()
				.jsonPath("$.type").isEqualTo(PROBLEM_BASE + "turnstile-failed")
				.jsonPath("$.status").isEqualTo(422);
	}

	@Test
	void upstreamFailureIsServiceUnavailableWithRetryAfter() {
		client.get().uri("/api/v1/test/upstream-unavailable")
				.exchange()
				.expectStatus().isEqualTo(503)
				.expectHeader().valueEquals(HttpHeaders.RETRY_AFTER, "17")
				.expectBody()
				.jsonPath("$.type").isEqualTo(PROBLEM_BASE + "upstream-unavailable")
				.jsonPath("$.status").isEqualTo(503);
	}

	@Test
	void unexpectedFailuresNeverLeakInternals() {
		byte[] body = client.get().uri("/api/v1/test/boom")
				.exchange()
				.expectStatus().isEqualTo(500)
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.type").isEqualTo(PROBLEM_BASE + "internal")
				.jsonPath("$.title").isEqualTo("Internal server error")
				.jsonPath("$.detail").isEqualTo("An unexpected error occurred.")
				.jsonPath("$.correlationId").exists()
				.returnResult()
				.getResponseBodyContent();

		assertThat(new String(body))
				.doesNotContain("IllegalStateException")
				.doesNotContain("must never be exposed");
	}

	@Test
	void rateLimitedEndpointsCarryTheHeadersAndEventuallyReturn429() {
		String path = "/api/v1/test/limited";
		long limit = RateLimitPolicy.GUESTBOOK.limit();

		for (long request = 1; request <= limit; request++) {
			client.get().uri(path)
					.exchange()
					.expectStatus().isOk()
					.expectHeader().valueEquals("RateLimit-Limit", Long.toString(limit))
					.expectHeader().valueEquals("RateLimit-Remaining", Long.toString(limit - request));
		}

		client.get().uri(path)
				.exchange()
				.expectStatus().isEqualTo(429)
				.expectHeader().exists(HttpHeaders.RETRY_AFTER)
				.expectHeader().valueEquals("RateLimit-Limit", Long.toString(limit))
				.expectHeader().valueEquals("RateLimit-Remaining", "0")
				.expectBody()
				.jsonPath("$.type").isEqualTo(PROBLEM_BASE + "rate-limited")
				.jsonPath("$.title").isEqualTo("Too many requests")
				.jsonPath("$.status").isEqualTo(429);
	}

	@Test
	void adminEndpointsRejectCallersWithoutTheToken() {
		client.get().uri("/api/v1/admin/test/ping")
				.exchange()
				.expectStatus().isUnauthorized()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.type").isEqualTo(PROBLEM_BASE + "unauthorized")
				.jsonPath("$.title").isEqualTo("Unauthorized")
				.jsonPath("$.status").isEqualTo(401);
	}

	@Test
	void adminEndpointsAcceptTheOwnerToken() {
		client.get().uri("/api/v1/admin/test/ping")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + TestAppProperties.ADMIN_TOKEN)
				.exchange()
				.expectStatus().isOk()
				.expectBody(String.class).isEqualTo("admin-pong");
	}

	@Test
	void unknownRoutesReturnAProblemResponse() {
		client.get().uri("/api/v1/does-not-exist")
				.exchange()
				.expectStatus().isNotFound()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.type").isEqualTo(PROBLEM_BASE + "not-found")
				.jsonPath("$.status").isEqualTo(404);
	}

	@Test
	void preflightFromTheConfiguredOriginIsAllowed() {
		client.options().uri("/api/v1/test/ok")
				.header(HttpHeaders.ORIGIN, "http://localhost:3000")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
				.exchange()
				.expectStatus().isOk()
				.expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000");
	}

	@Test
	void preflightFromAnUnknownOriginIsRejected() {
		client.options().uri("/api/v1/test/ok")
				.header(HttpHeaders.ORIGIN, "https://evil.example")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
				.exchange()
				.expectStatus().isForbidden();
	}

	@Test
	void healthEndpointIsUp() {
		client.get().uri("/actuator/health")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.status").isEqualTo("UP");
	}

	private boolean extensionExists(String name) {
		Integer count = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM pg_extension WHERE extname = ?", Integer.class, name);
		return count != null && count == 1;
	}

}
