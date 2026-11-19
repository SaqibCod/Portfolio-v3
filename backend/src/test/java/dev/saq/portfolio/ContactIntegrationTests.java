package dev.saq.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.util.List;
import java.util.Map;

import dev.saq.portfolio.client.TurnstileClient;
import dev.saq.portfolio.dto.external.TurnstileVerifyResponse;
import dev.saq.portfolio.model.DeliveryStatus;
import dev.saq.portfolio.model.RateLimitPolicy;
import dev.saq.portfolio.service.EmailService;
import dev.saq.testsupport.TestAppProperties;

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@code POST /api/v1/contact} end to end: a real servlet container, a real Postgres, and the
 * status codes, JSON shapes and headers the contract promises.
 *
 * <p>Two seams are replaced. {@link TurnstileClient} is mocked so the suite never calls
 * Cloudflare — the real {@code TurnstileService} still decides what each verdict means, including
 * its fail-closed behaviour. {@link EmailService} is spied rather than replaced: the test profile
 * already logs instead of sending, and the spy only makes the call observable.
 *
 * <p>Each test presents its own {@code X-Forwarded-For}. Rate-limit buckets are keyed by hashed
 * IP and {@code server.forward-headers-strategy=framework} makes Boot apply the header before any
 * application code sees the request, so this is what keeps a three-per-hour policy from making
 * the tests interfere with each other.
 */
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ContactIntegrationTests {

	private static final String PROBLEM_BASE = "https://api.example.com/problems/";

	private static final String CONTACT_PATH = "/api/v1/contact";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@MockitoBean
	private TurnstileClient turnstileClient;

	@MockitoSpyBean
	private EmailService emailService;

	@LocalServerPort
	private int port;

	private RestTestClient client;

	@BeforeEach
	void setUp() {
		client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
		jdbcTemplate.update("DELETE FROM contact_message");
	}

	@Test
	void acceptsAValidSubmissionAndStoresIt() {
		givenTurnstileAccepts();

		client.post().uri(CONTACT_PATH)
				.header("X-Forwarded-For", "203.0.113.10")
				.contentType(MediaType.APPLICATION_JSON)
				.body(body("ada@example.com", "A role for you"))
				.exchange()
				.expectStatus().isAccepted()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
				.expectBody()
				.jsonPath("$.id").exists()
				.jsonPath("$.status").isEqualTo("queued");

		Map<String, Object> row = onlyRow();
		assertThat(row.get("name")).isEqualTo("Ada Lovelace");
		assertThat(row.get("email")).isEqualTo("ada@example.com");
		assertThat(row.get("subject")).isEqualTo("A role for you");
		assertThat(row.get("message")).isEqualTo("I would like to talk to you about a backend role.");
	}

	@Test
	void emailsTheOwnerWithTheVisitorAsReplyTo() {
		givenTurnstileAccepts();

		postFrom("203.0.113.11", body("visitor@example.com", "A role for you"))
				.expectStatus().isAccepted();

		then(emailService).should().send(eq(TestAppProperties.OWNER_EMAIL),
				eq("[Portfolio] A role for you"), anyString(), eq("visitor@example.com"));
	}

	@Test
	void marksTheStoredMessageSentOnceDeliverySucceeds() {
		givenTurnstileAccepts();

		postFrom("203.0.113.12", body("ada@example.com", "A role for you"))
				.expectStatus().isAccepted();

		// The logging mail service completes immediately, so the status is already final here.
		assertThat(onlyRow().get("status")).isEqualTo(DeliveryStatus.SENT.name());
	}

	@Test
	void storesOnlyAHashedCallerAddress() {
		givenTurnstileAccepts();

		postFrom("203.0.113.13", body("ada@example.com", null)).expectStatus().isAccepted();

		String ipHash = (String) onlyRow().get("ip_hash");
		assertThat(ipHash).hasSize(64).matches("[0-9a-f]{64}").doesNotContain("203.0.113");
	}

	@Test
	void neverPersistsTheTurnstileToken() {
		givenTurnstileAccepts();

		postFrom("203.0.113.14", body("ada@example.com", "A role for you"))
				.expectStatus().isAccepted();

		assertThat(onlyRow().values().stream().map(String::valueOf))
				.noneMatch(value -> value.contains("token-from-the-widget"));
	}

	@Test
	void acceptsASubmissionWithoutASubject() {
		givenTurnstileAccepts();

		postFrom("203.0.113.15", body("ada@example.com", null)).expectStatus().isAccepted();

		assertThat(onlyRow().get("subject")).isNull();
		then(emailService).should().send(anyString(), eq("[Portfolio] Ada Lovelace"), anyString(),
				anyString());
	}

	@Test
	void answers202ButStoresNothingWhenTheHoneypotIsFilledIn() {
		client.post().uri(CONTACT_PATH)
				.header("X-Forwarded-For", "203.0.113.16")
				.contentType(MediaType.APPLICATION_JSON)
				.body("""
						{
						  "name": "Spam Bot",
						  "email": "bot@example.com",
						  "message": "Buy our excellent product today, friend.",
						  "turnstileToken": "token-from-the-widget",
						  "website": "https://spam.example"
						}""")
				.exchange()
				.expectStatus().isAccepted()
				.expectBody()
				.jsonPath("$.id").exists()
				.jsonPath("$.status").isEqualTo("queued");

		assertThat(rowCount()).isZero();
		then(emailService).should(never()).send(anyString(), anyString(), anyString(), anyString());
		// Not even verified: a trapped submission costs nothing upstream.
		then(turnstileClient).should(never()).verify(anyString(), anyString());
	}

	@Test
	void rejectsAnInvalidBodyWith400AndFieldErrors() {
		client.post().uri(CONTACT_PATH)
				.header("X-Forwarded-For", "203.0.113.17")
				.contentType(MediaType.APPLICATION_JSON)
				.body("""
						{"name": "", "email": "not-an-email", "message": "short", "turnstileToken": ""}""")
				.exchange()
				.expectStatus().isBadRequest()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.type").isEqualTo(PROBLEM_BASE + "validation")
				.jsonPath("$.title").isEqualTo("Validation failed")
				.jsonPath("$.status").isEqualTo(400)
				.jsonPath("$.errors[?(@.field == 'name')]").exists()
				.jsonPath("$.errors[?(@.field == 'email')]").exists()
				.jsonPath("$.errors[?(@.field == 'message')]").exists()
				.jsonPath("$.errors[?(@.field == 'turnstileToken')]").exists();

		assertThat(rowCount()).isZero();
	}

	@Test
	void rejectsAFailedTurnstileCheckWith422() {
		given(turnstileClient.verify(anyString(), any()))
				.willReturn(new TurnstileVerifyResponse(false, null, null, List.of("invalid-input-response")));

		postFrom("203.0.113.18", body("ada@example.com", "A role for you"))
				.expectStatus().isEqualTo(422)
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.type").isEqualTo(PROBLEM_BASE + "turnstile-failed")
				.jsonPath("$.title").isEqualTo("Turnstile verification failed")
				.jsonPath("$.status").isEqualTo(422);

		assertThat(rowCount()).isZero();
		then(emailService).should(never()).send(anyString(), anyString(), anyString(), anyString());
	}

	@Test
	void failsClosedWith503WhenCloudflareIsUnreachable() {
		given(turnstileClient.verify(anyString(), any()))
				.willThrow(new RestClientException("cloudflare is down"));

		postFrom("203.0.113.19", body("ada@example.com", "A role for you"))
				.expectStatus().isEqualTo(503)
				.expectHeader().exists(HttpHeaders.RETRY_AFTER)
				.expectBody()
				.jsonPath("$.type").isEqualTo(PROBLEM_BASE + "upstream-unavailable")
				.jsonPath("$.status").isEqualTo(503);

		// An outage must not become a way past the bot check.
		assertThat(rowCount()).isZero();
		then(emailService).should(never()).send(anyString(), anyString(), anyString(), anyString());
	}

	@Test
	void refusesTheFourthSubmissionInAnHourWith429() {
		givenTurnstileAccepts();
		long limit = RateLimitPolicy.CONTACT.limit();

		for (long attempt = 1; attempt <= limit; attempt++) {
			postFrom("203.0.113.20", body("ada@example.com", "A role for you"))
					.expectStatus().isAccepted()
					.expectHeader().valueEquals("RateLimit-Limit", Long.toString(limit))
					.expectHeader().valueEquals("RateLimit-Remaining", Long.toString(limit - attempt));
		}

		postFrom("203.0.113.20", body("ada@example.com", "A role for you"))
				.expectStatus().isEqualTo(429)
				.expectHeader().exists(HttpHeaders.RETRY_AFTER)
				.expectHeader().valueEquals("RateLimit-Remaining", "0")
				.expectBody()
				.jsonPath("$.type").isEqualTo(PROBLEM_BASE + "rate-limited")
				.jsonPath("$.title").isEqualTo("Too many requests")
				.jsonPath("$.status").isEqualTo(429);

		assertThat(rowCount()).isEqualTo(limit);
	}

	@Test
	void countsTheBudgetPerCaller() {
		givenTurnstileAccepts();

		for (long attempt = 1; attempt <= RateLimitPolicy.CONTACT.limit(); attempt++) {
			postFrom("203.0.113.21", body("ada@example.com", null)).expectStatus().isAccepted();
		}
		postFrom("203.0.113.21", body("ada@example.com", null)).expectStatus().isEqualTo(429);

		// A different visitor behind the same proxy still gets a full allowance.
		postFrom("203.0.113.22", body("ada@example.com", null)).expectStatus().isAccepted();
	}

	@Test
	void rejectsASubmissionWithNoBotCheckAtAll() {
		client.post().uri(CONTACT_PATH)
				.header("X-Forwarded-For", "203.0.113.23")
				.contentType(MediaType.APPLICATION_JSON)
				.body("""
						{
						  "name": "Ada Lovelace",
						  "email": "ada@example.com",
						  "message": "I would like to talk to you about a backend role."
						}""")
				.exchange()
				.expectStatus().isBadRequest()
				.expectBody()
				.jsonPath("$.errors[?(@.field == 'turnstileToken')]").exists();

		assertThat(rowCount()).isZero();
		then(turnstileClient).should(never()).verify(anyString(), anyString());
	}

	private void givenTurnstileAccepts() {
		given(turnstileClient.verify(anyString(), any()))
				.willReturn(new TurnstileVerifyResponse(true, "2026-11-19T00:00:00Z", "example.com",
						List.of()));
	}

	private RestTestClient.ResponseSpec postFrom(String ip, String body) {
		return client.post().uri(CONTACT_PATH)
				.header("X-Forwarded-For", ip)
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.exchange();
	}

	private static String body(String email, String subject) {
		String subjectLine = (subject != null) ? "\"subject\": \"%s\",%n".formatted(subject) : "";
		return """
				{
				  "name": "Ada Lovelace",
				  "email": "%s",
				  %s  "message": "I would like to talk to you about a backend role.",
				  "turnstileToken": "token-from-the-widget"
				}""".formatted(email, subjectLine);
	}

	private Map<String, Object> onlyRow() {
		List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM contact_message");
		assertThat(rows).hasSize(1);
		return rows.getFirst();
	}

	private long rowCount() {
		Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM contact_message", Long.class);
		return (count != null) ? count : 0;
	}

}
