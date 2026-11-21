package dev.saq.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.saq.portfolio.client.TurnstileClient;
import dev.saq.portfolio.dto.external.TurnstileVerifyResponse;
import dev.saq.portfolio.model.GuestbookStatus;
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

/**
 * The five guestbook endpoints end to end: a real servlet container, a real Postgres, and the
 * status codes, JSON shapes and headers the contract promises.
 *
 * <p>The approval gate is the point of most of this. A note is only ever visible publicly after
 * the owner approves it, so the tests here work the same way a person would — post, read the
 * public list, moderate through {@code /api/v1/admin}, read the public list again.
 *
 * <p>Two seams are replaced, as in {@link ContactIntegrationTests}. {@link TurnstileClient} is
 * mocked so the suite never calls Cloudflare — the real {@code TurnstileService} still decides
 * what each verdict means. {@link EmailService} is spied rather than replaced: the test profile
 * already logs instead of sending, and the spy only makes the call observable.
 *
 * <p>Each test presents its own {@code X-Forwarded-For}. The guestbook policy is two entries per
 * IP per day, so a shared address would run out inside a single test, let alone across the class.
 */
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GuestbookIntegrationTests {

	private static final String PROBLEM_BASE = "https://api.example.com/problems/";

	private static final String PATH = "/api/v1/guestbook";

	private static final String ADMIN_PATH = "/api/v1/admin/guestbook";

	private static final String BEARER = "Bearer " + TestAppProperties.ADMIN_TOKEN;

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
		jdbcTemplate.update("DELETE FROM guestbook_entry");
	}

	// --- POST /api/v1/guestbook ---------------------------------------------------------------

	@Test
	void acceptsANoteAndHoldsItForApproval() {
		givenTurnstileAccepts();

		postFrom("203.0.113.60", body("Priya", "Loved the status page idea!"))
				.expectStatus().isAccepted()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
				.expectBody()
				.jsonPath("$.id").exists()
				.jsonPath("$.status").isEqualTo("pending");

		Map<String, Object> row = onlyRow();
		assertThat(row.get("name")).isEqualTo("Priya");
		assertThat(row.get("message")).isEqualTo("Loved the status page idea!");
		assertThat(row.get("status")).isEqualTo(GuestbookStatus.PENDING.name());
		assertThat(row.get("moderated_at")).isNull();
	}

	@Test
	void emailsTheOwnerAboutEachNewPendingEntry() {
		givenTurnstileAccepts();

		postFrom("203.0.113.61", body("Priya", "Loved the status page idea!"))
				.expectStatus().isAccepted();

		then(emailService).should().send(eq(TestAppProperties.OWNER_EMAIL),
				eq("[Portfolio] New guestbook entry from Priya"),
				contains("Loved the status page idea!"), eq(null));
	}

	@Test
	void keepsANewNoteOffThePublicListUntilItIsApproved() {
		givenTurnstileAccepts();

		postFrom("203.0.113.62", body("Priya", "Loved the status page idea!"))
				.expectStatus().isAccepted();

		// The acceptance criterion: posted, stored, and still invisible.
		assertThat(rowCount()).isEqualTo(1);
		publicList("").expectStatus().isOk().expectBody().jsonPath("$.items").isEmpty();
	}

	@Test
	void storesOnlyAHashedCallerAddress() {
		givenTurnstileAccepts();

		postFrom("203.0.113.63", body("Priya", "Hello there!")).expectStatus().isAccepted();

		String ipHash = (String) onlyRow().get("ip_hash");
		assertThat(ipHash).hasSize(64).matches("[0-9a-f]{64}").doesNotContain("203.0.113");
	}

	@Test
	void neverPersistsTheTurnstileToken() {
		givenTurnstileAccepts();

		postFrom("203.0.113.64", body("Priya", "Hello there!")).expectStatus().isAccepted();

		assertThat(onlyRow().values().stream().map(String::valueOf))
				.noneMatch(value -> value.contains("token-from-the-widget"));
	}

	@Test
	void answers202ButStoresNothingWhenTheHoneypotIsFilledIn() {
		client.post().uri(PATH)
				.header("X-Forwarded-For", "203.0.113.65")
				.contentType(MediaType.APPLICATION_JSON)
				.body("""
						{
						  "name": "Spam Bot",
						  "message": "Buy our excellent product today, friend.",
						  "turnstileToken": "token-from-the-widget",
						  "website": "https://spam.example"
						}""")
				.exchange()
				.expectStatus().isAccepted()
				.expectBody()
				.jsonPath("$.id").exists()
				.jsonPath("$.status").isEqualTo("pending");

		assertThat(rowCount()).isZero();
		then(emailService).should(never()).send(anyString(), anyString(), anyString(), any());
		// Not even verified: a trapped submission costs nothing upstream.
		then(turnstileClient).should(never()).verify(anyString(), anyString());
	}

	@Test
	void rejectsAnInvalidBodyWith400AndFieldErrors() {
		client.post().uri(PATH)
				.header("X-Forwarded-For", "203.0.113.66")
				.contentType(MediaType.APPLICATION_JSON)
				.body("""
						{"name": "", "message": "x", "turnstileToken": ""}""")
				.exchange()
				.expectStatus().isBadRequest()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.type").isEqualTo(PROBLEM_BASE + "validation")
				.jsonPath("$.title").isEqualTo("Validation failed")
				.jsonPath("$.status").isEqualTo(400)
				.jsonPath("$.errors[?(@.field == 'name')]").exists()
				.jsonPath("$.errors[?(@.field == 'message')]").exists()
				.jsonPath("$.errors[?(@.field == 'turnstileToken')]").exists();

		assertThat(rowCount()).isZero();
	}

	@Test
	void rejectsAFailedTurnstileCheckWith422() {
		given(turnstileClient.verify(anyString(), any())).willReturn(
				new TurnstileVerifyResponse(false, null, null, List.of("invalid-input-response")));

		postFrom("203.0.113.67", body("Priya", "Hello there!"))
				.expectStatus().isEqualTo(422)
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.type").isEqualTo(PROBLEM_BASE + "turnstile-failed")
				.jsonPath("$.title").isEqualTo("Turnstile verification failed")
				.jsonPath("$.status").isEqualTo(422);

		assertThat(rowCount()).isZero();
		then(emailService).should(never()).send(anyString(), anyString(), anyString(), any());
	}

	@Test
	void refusesTheThirdNoteInADayWith429() {
		givenTurnstileAccepts();
		long limit = RateLimitPolicy.GUESTBOOK.limit();

		for (long attempt = 1; attempt <= limit; attempt++) {
			postFrom("203.0.113.68", body("Priya", "Hello there!"))
					.expectStatus().isAccepted()
					.expectHeader().valueEquals("RateLimit-Limit", Long.toString(limit))
					.expectHeader().valueEquals("RateLimit-Remaining", Long.toString(limit - attempt));
		}

		postFrom("203.0.113.68", body("Priya", "Hello there!"))
				.expectStatus().isEqualTo(429)
				.expectHeader().exists(HttpHeaders.RETRY_AFTER)
				.expectHeader().valueEquals("RateLimit-Remaining", "0")
				.expectBody()
				.jsonPath("$.type").isEqualTo(PROBLEM_BASE + "rate-limited")
				.jsonPath("$.title").isEqualTo("Too many requests")
				.jsonPath("$.status").isEqualTo(429);

		assertThat(rowCount()).isEqualTo(limit);
	}

	// --- GET /api/v1/guestbook ----------------------------------------------------------------

	@Test
	void servesOnlyApprovedEntriesPublicly() {
		insert("approved note", GuestbookStatus.APPROVED, Instant.parse("2026-11-20T10:00:00Z"));
		insert("pending note", GuestbookStatus.PENDING, Instant.parse("2026-11-20T11:00:00Z"));
		insert("rejected note", GuestbookStatus.REJECTED, Instant.parse("2026-11-20T12:00:00Z"));

		publicList("").expectStatus().isOk()
				.expectBody()
				.jsonPath("$.items.length()").isEqualTo(1)
				.jsonPath("$.items[0].message").isEqualTo("approved note")
				.jsonPath("$.nextCursor").isEmpty();
	}

	@Test
	void servesApprovedEntriesNewestFirst() {
		insert("oldest", GuestbookStatus.APPROVED, Instant.parse("2026-11-20T10:00:00Z"));
		insert("middle", GuestbookStatus.APPROVED, Instant.parse("2026-11-20T11:00:00Z"));
		insert("newest", GuestbookStatus.APPROVED, Instant.parse("2026-11-20T12:00:00Z"));

		publicList("").expectStatus().isOk()
				.expectBody()
				.jsonPath("$.items[0].message").isEqualTo("newest")
				.jsonPath("$.items[1].message").isEqualTo("middle")
				.jsonPath("$.items[2].message").isEqualTo("oldest");
	}

	@Test
	void exposesNoStatusOrHashOnThePublicList() {
		insert("approved note", GuestbookStatus.APPROVED, Instant.parse("2026-11-20T10:00:00Z"));

		publicList("").expectStatus().isOk()
				.expectBody()
				.jsonPath("$.items[0].id").exists()
				.jsonPath("$.items[0].name").isEqualTo("Priya")
				.jsonPath("$.items[0].createdAt").exists()
				.jsonPath("$.items[0].status").doesNotExist()
				.jsonPath("$.items[0].moderatedAt").doesNotExist()
				.jsonPath("$.items[0].ipHash").doesNotExist();
	}

	@Test
	void pagesThroughSevenEntriesInThreePagesWithoutRepeatingOrLosingOne() {
		seedApproved(7);

		List<String> walked = new ArrayList<>();
		String cursor = null;
		int pages = 0;
		do {
			String query = (cursor == null) ? "?limit=3" : "?limit=3&cursor=" + cursor;
			Map<String, Object> page = publicPage(query);
			walked.addAll(messagesOf(page));
			cursor = (String) page.get("nextCursor");
			pages++;
		}
		while (cursor != null);

		assertThat(pages).isEqualTo(3);
		// Stable order, every entry exactly once: 7 newest-first, 3 + 3 + 1.
		assertThat(walked).containsExactly("note 7", "note 6", "note 5", "note 4", "note 3",
				"note 2", "note 1");
		assertThat(walked).doesNotHaveDuplicates();
	}

	@Test
	void stopsOfferingACursorOnTheLastPage() {
		seedApproved(4);

		Map<String, Object> first = publicPage("?limit=3");
		assertThat(first.get("nextCursor")).isNotNull();

		Map<String, Object> second = publicPage("?limit=3&cursor=" + first.get("nextCursor"));
		assertThat(messagesOf(second)).containsExactly("note 1");
		assertThat(second.get("nextCursor")).isNull();
	}

	@Test
	void rejectsACursorItDidNotIssueWith400() {
		seedApproved(2);

		publicList("?cursor=not-a-cursor")
				.expectStatus().isBadRequest()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.type").isEqualTo(PROBLEM_BASE + "validation")
				.jsonPath("$.status").isEqualTo(400)
				.jsonPath("$.errors[0].field").isEqualTo("cursor");
	}

	@Test
	void neverEchoesARejectedCursorBack() {
		publicList("?cursor=PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg")
				.expectStatus().isBadRequest()
				.expectBody()
				.jsonPath("$.detail").value((String detail) -> assertThat(detail)
						.doesNotContain("script"));
	}

	@Test
	void servesAnEmptyPageWhenNothingIsApprovedYet() {
		publicList("").expectStatus().isOk()
				.expectBody()
				.jsonPath("$.items").isEmpty()
				.jsonPath("$.nextCursor").isEmpty();
	}

	// --- GET /api/v1/admin/guestbook ----------------------------------------------------------

	@Test
	void refusesEveryAdminEndpointWithoutAToken() {
		UUID id = insert("pending note", GuestbookStatus.PENDING,
				Instant.parse("2026-11-20T10:00:00Z"));

		client.get().uri(ADMIN_PATH).exchange()
				.expectStatus().isUnauthorized()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.type").isEqualTo(PROBLEM_BASE + "unauthorized")
				.jsonPath("$.status").isEqualTo(401);

		client.patch().uri(ADMIN_PATH + "/" + id)
				.contentType(MediaType.APPLICATION_JSON)
				.body("""
						{"status": "approved"}""")
				.exchange().expectStatus().isUnauthorized();

		client.delete().uri(ADMIN_PATH + "/" + id).exchange().expectStatus().isUnauthorized();

		// Nothing changed behind the gate.
		assertThat(onlyRow().get("status")).isEqualTo(GuestbookStatus.PENDING.name());
	}

	@Test
	void showsTheOwnerThePendingQueueByDefault() {
		insert("pending note", GuestbookStatus.PENDING, Instant.parse("2026-11-20T10:00:00Z"));
		insert("approved note", GuestbookStatus.APPROVED, Instant.parse("2026-11-20T11:00:00Z"));

		adminList("").expectStatus().isOk()
				.expectBody()
				.jsonPath("$.items.length()").isEqualTo(1)
				.jsonPath("$.items[0].message").isEqualTo("pending note")
				.jsonPath("$.items[0].status").isEqualTo("pending")
				.jsonPath("$.items[0].moderatedAt").isEmpty();
	}

	@Test
	void filtersTheOwnersListByEachStatus() {
		insert("pending note", GuestbookStatus.PENDING, Instant.parse("2026-11-20T10:00:00Z"));
		insert("approved note", GuestbookStatus.APPROVED, Instant.parse("2026-11-20T11:00:00Z"));
		insert("rejected note", GuestbookStatus.REJECTED, Instant.parse("2026-11-20T12:00:00Z"));

		adminList("?status=approved").expectStatus().isOk().expectBody()
				.jsonPath("$.items.length()").isEqualTo(1)
				.jsonPath("$.items[0].message").isEqualTo("approved note");
		adminList("?status=rejected").expectStatus().isOk().expectBody()
				.jsonPath("$.items.length()").isEqualTo(1)
				.jsonPath("$.items[0].message").isEqualTo("rejected note");
	}

	@Test
	void rejectsAStatusFilterTheContractDoesNotDefine() {
		adminList("?status=queued")
				.expectStatus().isBadRequest()
				.expectBody()
				.jsonPath("$.type").isEqualTo(PROBLEM_BASE + "validation")
				.jsonPath("$.status").isEqualTo(400);
	}

	// --- PATCH /api/v1/admin/guestbook/{entryId} -----------------------------------------------

	@Test
	void approvingAnEntryMakesItPublic() {
		UUID id = insert("waiting note", GuestbookStatus.PENDING,
				Instant.parse("2026-11-20T10:00:00Z"));
		publicList("").expectStatus().isOk().expectBody().jsonPath("$.items").isEmpty();

		moderate(id, "approved")
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.id").isEqualTo(id.toString())
				.jsonPath("$.status").isEqualTo("approved")
				.jsonPath("$.moderatedAt").exists();

		publicList("").expectStatus().isOk()
				.expectBody()
				.jsonPath("$.items.length()").isEqualTo(1)
				.jsonPath("$.items[0].message").isEqualTo("waiting note");
	}

	@Test
	void rejectingAnEntryKeepsItHidden() {
		UUID id = insert("spam note", GuestbookStatus.PENDING, Instant.parse("2026-11-20T10:00:00Z"));

		moderate(id, "rejected")
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.status").isEqualTo("rejected")
				.jsonPath("$.moderatedAt").exists();

		publicList("").expectStatus().isOk().expectBody().jsonPath("$.items").isEmpty();
		// Kept rather than deleted, so the same note cannot simply be resubmitted.
		assertThat(rowCount()).isEqualTo(1);
	}

	@Test
	void unpublishingAnApprovedEntryTakesItBackOffTheSite() {
		UUID id = insert("published note", GuestbookStatus.APPROVED,
				Instant.parse("2026-11-20T10:00:00Z"));
		publicList("").expectStatus().isOk().expectBody().jsonPath("$.items.length()").isEqualTo(1);

		moderate(id, "rejected").expectStatus().isOk();

		publicList("").expectStatus().isOk().expectBody().jsonPath("$.items").isEmpty();
	}

	@Test
	void leavesTheNoteItselfUntouchedWhenModerating() {
		UUID id = insert("the original words", GuestbookStatus.PENDING,
				Instant.parse("2026-11-20T10:00:00Z"));

		moderate(id, "approved").expectStatus().isOk();

		Map<String, Object> row = onlyRow();
		assertThat(row.get("message")).isEqualTo("the original words");
		assertThat(row.get("name")).isEqualTo("Priya");
	}

	@Test
	void reportsModeratingAnEntryThatDoesNotExistAs404() {
		moderate(UUID.randomUUID(), "approved")
				.expectStatus().isNotFound()
				.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.type").isEqualTo(PROBLEM_BASE + "not-found")
				.jsonPath("$.status").isEqualTo(404);
	}

	@Test
	void refusesToModerateAnEntryBackToPending() {
		UUID id = insert("waiting note", GuestbookStatus.PENDING,
				Instant.parse("2026-11-20T10:00:00Z"));

		moderate(id, "pending")
				.expectStatus().isBadRequest()
				.expectBody()
				.jsonPath("$.type").isEqualTo(PROBLEM_BASE + "validation")
				.jsonPath("$.errors[?(@.field == 'status')]").exists();

		assertThat(onlyRow().get("status")).isEqualTo(GuestbookStatus.PENDING.name());
	}

	// --- DELETE /api/v1/admin/guestbook/{entryId} ----------------------------------------------

	@Test
	void deletesAnEntryWith204AndThenReports404() {
		UUID id = insert("spam note", GuestbookStatus.PENDING, Instant.parse("2026-11-20T10:00:00Z"));

		client.delete().uri(ADMIN_PATH + "/" + id)
				.header(HttpHeaders.AUTHORIZATION, BEARER)
				.exchange()
				.expectStatus().isNoContent()
				.expectBody().isEmpty();

		assertThat(rowCount()).isZero();

		client.delete().uri(ADMIN_PATH + "/" + id)
				.header(HttpHeaders.AUTHORIZATION, BEARER)
				.exchange()
				.expectStatus().isNotFound()
				.expectBody()
				.jsonPath("$.type").isEqualTo(PROBLEM_BASE + "not-found")
				.jsonPath("$.status").isEqualTo(404);
	}

	@Test
	void deletingAnApprovedEntryTakesItOffThePublicList() {
		UUID id = insert("published note", GuestbookStatus.APPROVED,
				Instant.parse("2026-11-20T10:00:00Z"));

		client.delete().uri(ADMIN_PATH + "/" + id)
				.header(HttpHeaders.AUTHORIZATION, BEARER)
				.exchange()
				.expectStatus().isNoContent();

		publicList("").expectStatus().isOk().expectBody().jsonPath("$.items").isEmpty();
	}

	// --- the whole round trip -----------------------------------------------------------------

	@Test
	void takesANoteFromSubmissionToThePageThroughApproval() {
		givenTurnstileAccepts();

		postFrom("203.0.113.69", body("Priya", "A note that will be approved."))
				.expectStatus().isAccepted();
		then(emailService).should().send(eq(TestAppProperties.OWNER_EMAIL), anyString(), anyString(),
				eq(null));
		publicList("").expectStatus().isOk().expectBody().jsonPath("$.items").isEmpty();

		UUID id = (UUID) onlyRow().get("id");
		moderate(id, "approved").expectStatus().isOk();

		publicList("").expectStatus().isOk()
				.expectBody()
				.jsonPath("$.items.length()").isEqualTo(1)
				.jsonPath("$.items[0].message").isEqualTo("A note that will be approved.")
				.jsonPath("$.items[0].name").isEqualTo("Priya");
	}

	// --- fixtures -----------------------------------------------------------------------------

	private void givenTurnstileAccepts() {
		given(turnstileClient.verify(anyString(), any())).willReturn(new TurnstileVerifyResponse(
				true, "2026-11-20T00:00:00Z", "example.com", List.of()));
	}

	private RestTestClient.ResponseSpec postFrom(String ip, String body) {
		return client.post().uri(PATH)
				.header("X-Forwarded-For", ip)
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.exchange();
	}

	private static String body(String name, String message) {
		return """
				{
				  "name": "%s",
				  "message": "%s",
				  "turnstileToken": "token-from-the-widget"
				}""".formatted(name, message);
	}

	/** Reads are capped at 120 a minute, which no test here comes close to, so one IP is fine. */
	private RestTestClient.ResponseSpec publicList(String query) {
		return client.get().uri(PATH + query)
				.header("X-Forwarded-For", "203.0.113.90")
				.exchange();
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> publicPage(String query) {
		return publicList(query).expectStatus().isOk()
				.expectBody(Map.class)
				.returnResult()
				.getResponseBody();
	}

	@SuppressWarnings("unchecked")
	private static List<String> messagesOf(Map<String, Object> page) {
		return ((List<Map<String, Object>>) page.get("items")).stream()
				.map(item -> (String) item.get("message"))
				.toList();
	}

	private RestTestClient.ResponseSpec adminList(String query) {
		return client.get().uri(ADMIN_PATH + query)
				.header(HttpHeaders.AUTHORIZATION, BEARER)
				.exchange();
	}

	private RestTestClient.ResponseSpec moderate(UUID id, String status) {
		return client.patch().uri(ADMIN_PATH + "/" + id)
				.header(HttpHeaders.AUTHORIZATION, BEARER)
				.contentType(MediaType.APPLICATION_JSON)
				.body("""
						{"status": "%s"}""".formatted(status))
				.exchange();
	}

	/** {@code count} approved entries, "note 1" oldest through "note n" newest. */
	private void seedApproved(int count) {
		Instant base = Instant.parse("2026-11-20T10:00:00Z");
		for (int index = 1; index <= count; index++) {
			insert("note " + index, GuestbookStatus.APPROVED, base.plusSeconds(index * 60L));
		}
	}

	/**
	 * Inserts one entry directly, with an explicit {@code created_at}.
	 *
	 * <p>Going through the API instead would cap a test at two entries per address and would
	 * stamp every row with the same {@code now()}, which is exactly the ordering the page queries
	 * need to be able to tell apart.
	 */
	private UUID insert(String message, GuestbookStatus status, Instant createdAt) {
		UUID id = UUID.randomUUID();
		jdbcTemplate.update("""
				INSERT INTO guestbook_entry (id, name, message, status, ip_hash, created_at)
				VALUES (?, 'Priya', ?, ?, ?, ?)""",
				id, message, status.name(), "0123456789abcdef".repeat(4),
				createdAt.atOffset(ZoneOffset.UTC));
		return id;
	}

	private Map<String, Object> onlyRow() {
		List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM guestbook_entry");
		assertThat(rows).hasSize(1);
		return rows.getFirst();
	}

	private long rowCount() {
		Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM guestbook_entry", Long.class);
		return (count != null) ? count : 0;
	}

}
