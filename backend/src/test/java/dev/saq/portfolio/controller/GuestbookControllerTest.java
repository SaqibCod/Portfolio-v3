package dev.saq.portfolio.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.saq.portfolio.dto.request.CreateGuestbookEntryRequest;
import dev.saq.portfolio.dto.response.GuestbookEntryResponse;
import dev.saq.portfolio.dto.response.GuestbookPageResponse;
import dev.saq.portfolio.dto.response.GuestbookSubmissionResponse;
import dev.saq.portfolio.exception.InvalidCursorException;
import dev.saq.portfolio.exception.TurnstileFailedException;
import dev.saq.portfolio.model.RateLimitPolicy;
import dev.saq.portfolio.service.GuestbookService;
import dev.saq.testsupport.WebInfrastructureTestConfig;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * HTTP in, HTTP out: status codes, the page and 202 bodies, parameter validation and the
 * rate-limit headers, with the service mocked.
 *
 * <p>Every request comes from its own remote address. The rate-limit interceptor is live in this
 * slice and buckets are keyed by hashed IP, so distinct addresses keep the tests independent
 * despite the shared application context — and the guestbook policy is only two per day, which
 * one shared address would exhaust almost immediately.
 */
@WebMvcTest(GuestbookController.class)
@Import(WebInfrastructureTestConfig.class)
@ActiveProfiles("test")
class GuestbookControllerTest {

	private static final String PROBLEM_BASE = "https://api.example.com/problems/";

	private static final String PATH = "/api/v1/guestbook";

	private static final String VALID_BODY = """
			{
			  "name": "Priya",
			  "message": "Loved the status page idea!",
			  "turnstileToken": "token-from-the-widget"
			}""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private GuestbookService guestbookService;

	// --- GET ----------------------------------------------------------------------------------

	@Test
	void servesAPageOfApprovedEntries() throws Exception {
		UUID id = UUID.fromString("11111111-2222-3333-4444-555555555555");
		given(guestbookService.listApproved(any(), anyInt())).willReturn(new GuestbookPageResponse(
				List.of(new GuestbookEntryResponse(id, "Priya", "Loved it!",
						Instant.parse("2026-11-20T10:15:00Z"))),
				"next-cursor"));

		mockMvc.perform(get("203.0.113.30", PATH))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].id").value(id.toString()))
				.andExpect(jsonPath("$.items[0].name").value("Priya"))
				.andExpect(jsonPath("$.items[0].message").value("Loved it!"))
				.andExpect(jsonPath("$.items[0].createdAt").value("2026-11-20T10:15:00Z"))
				.andExpect(jsonPath("$.nextCursor").value("next-cursor"));
	}

	@Test
	void neverExposesAStatusOnAPublicEntry() throws Exception {
		given(guestbookService.listApproved(any(), anyInt())).willReturn(new GuestbookPageResponse(
				List.of(new GuestbookEntryResponse(UUID.randomUUID(), "Priya", "Loved it!",
						Instant.now())),
				null));

		mockMvc.perform(get("203.0.113.31", PATH))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].status").doesNotExist())
				.andExpect(jsonPath("$.items[0].moderatedAt").doesNotExist())
				.andExpect(jsonPath("$.items[0].ipHash").doesNotExist());
	}

	@Test
	void serialisesAnExhaustedListAsAnExplicitNullCursor() throws Exception {
		given(guestbookService.listApproved(any(), anyInt()))
				.willReturn(new GuestbookPageResponse(List.of(), null));

		// The contract makes nextCursor required, and a client checks it to decide whether to
		// offer "load more", so it has to be present even when it is null.
		mockMvc.perform(get("203.0.113.32", PATH))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isArray())
				.andExpect(jsonPath("$.nextCursor").isEmpty())
				.andExpect(content -> assertThat(content.getResponse().getContentAsString())
						.contains("nextCursor"));
	}

	@Test
	void defaultsToTwentyEntriesAndNoCursor() throws Exception {
		given(guestbookService.listApproved(any(), anyInt()))
				.willReturn(new GuestbookPageResponse(List.of(), null));

		mockMvc.perform(get("203.0.113.33", PATH)).andExpect(status().isOk());

		then(guestbookService).should().listApproved(null, 20);
	}

	@Test
	void passesTheCursorAndLimitStraightThrough() throws Exception {
		given(guestbookService.listApproved(any(), anyInt()))
				.willReturn(new GuestbookPageResponse(List.of(), null));

		mockMvc.perform(get("203.0.113.34", PATH + "?cursor=abc123&limit=5"))
				.andExpect(status().isOk());

		then(guestbookService).should().listApproved("abc123", 5);
	}

	@Test
	void rejectsALimitBelowOne() throws Exception {
		mockMvc.perform(get("203.0.113.35", PATH + "?limit=0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value(PROBLEM_BASE + "validation"));

		then(guestbookService).should(never()).listApproved(any(), anyInt());
	}

	@Test
	void rejectsALimitAboveFifty() throws Exception {
		mockMvc.perform(get("203.0.113.36", PATH + "?limit=51"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value(PROBLEM_BASE + "validation"));

		then(guestbookService).should(never()).listApproved(any(), anyInt());
	}

	@Test
	void rejectsALimitThatIsNotANumber() throws Exception {
		mockMvc.perform(get("203.0.113.37", PATH + "?limit=lots"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value(PROBLEM_BASE + "validation"));
	}

	@Test
	void surfacesAnUndecodableCursorAs400() throws Exception {
		willThrow(new InvalidCursorException("Cursor is malformed."))
				.given(guestbookService).listApproved(anyString(), anyInt());

		mockMvc.perform(get("203.0.113.38", PATH + "?cursor=not-a-cursor"))
				.andExpect(status().isBadRequest())
				.andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
				.andExpect(jsonPath("$.type").value(PROBLEM_BASE + "validation"))
				.andExpect(jsonPath("$.errors[0].field").value("cursor"));
	}

	@Test
	void carriesTheReadRateLimitHeaders() throws Exception {
		given(guestbookService.listApproved(any(), anyInt()))
				.willReturn(new GuestbookPageResponse(List.of(), null));

		mockMvc.perform(get("203.0.113.39", PATH))
				.andExpect(status().isOk())
				.andExpect(header().string("RateLimit-Limit",
						Long.toString(RateLimitPolicy.PUBLIC_READ.limit())))
				.andExpect(header().string("RateLimit-Remaining",
						Long.toString(RateLimitPolicy.PUBLIC_READ.limit() - 1)));
	}

	// --- POST ---------------------------------------------------------------------------------

	@Test
	void acceptsAValidSubmissionWith202() throws Exception {
		UUID id = UUID.fromString("11111111-2222-3333-4444-555555555555");
		given(guestbookService.create(any(), any()))
				.willReturn(GuestbookSubmissionResponse.pending(id));

		mockMvc.perform(post("203.0.113.40", VALID_BODY))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.id").value(id.toString()))
				.andExpect(jsonPath("$.status").value("pending"));
	}

	@Test
	void passesTheParsedRequestAndCallerAddressToTheService() throws Exception {
		given(guestbookService.create(any(), any()))
				.willReturn(GuestbookSubmissionResponse.pending(UUID.randomUUID()));

		mockMvc.perform(post("203.0.113.41", VALID_BODY)).andExpect(status().isAccepted());

		ArgumentCaptor<CreateGuestbookEntryRequest> request = ArgumentCaptor
				.forClass(CreateGuestbookEntryRequest.class);
		ArgumentCaptor<String> ip = ArgumentCaptor.forClass(String.class);
		then(guestbookService).should().create(request.capture(), ip.capture());

		assertThat(request.getValue().name()).isEqualTo("Priya");
		assertThat(request.getValue().message()).isEqualTo("Loved the status page idea!");
		assertThat(request.getValue().turnstileToken()).isEqualTo("token-from-the-widget");
		assertThat(ip.getValue()).isEqualTo("203.0.113.41");
	}

	@Test
	void reportsEveryInvalidFieldAsA400() throws Exception {
		String body = """
				{"name": "", "message": "x", "turnstileToken": ""}""";

		mockMvc.perform(post("203.0.113.42", body))
				.andExpect(status().isBadRequest())
				.andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
				.andExpect(jsonPath("$.type").value(PROBLEM_BASE + "validation"))
				.andExpect(jsonPath("$.title").value("Validation failed"))
				.andExpect(jsonPath("$.errors[?(@.field == 'name')]").exists())
				.andExpect(jsonPath("$.errors[?(@.field == 'message')]").exists())
				.andExpect(jsonPath("$.errors[?(@.field == 'turnstileToken')]").exists());

		then(guestbookService).should(never()).create(any(), any());
	}

	@Test
	void rejectsANameOverTheContractsLimit() throws Exception {
		String body = """
				{
				  "name": "%s",
				  "message": "Loved the status page idea!",
				  "turnstileToken": "token-from-the-widget"
				}""".formatted("n".repeat(51));

		mockMvc.perform(post("203.0.113.43", body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[?(@.field == 'name')]").exists());

		then(guestbookService).should(never()).create(any(), any());
	}

	@Test
	void rejectsAMessageOverTheContractsLimit() throws Exception {
		String body = """
				{
				  "name": "Priya",
				  "message": "%s",
				  "turnstileToken": "token-from-the-widget"
				}""".formatted("m".repeat(501));

		mockMvc.perform(post("203.0.113.44", body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[?(@.field == 'message')]").exists());
	}

	@Test
	void rejectsAMalformedBody() throws Exception {
		mockMvc.perform(post("203.0.113.45", "{not json"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value(PROBLEM_BASE + "validation"))
				.andExpect(jsonPath("$.errors[0].field").value("body"));
	}

	@Test
	void surfacesATurnstileRejectionAs422() throws Exception {
		willThrow(new TurnstileFailedException("Turnstile verification failed."))
				.given(guestbookService).create(any(), any());

		mockMvc.perform(post("203.0.113.46", VALID_BODY))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.type").value(PROBLEM_BASE + "turnstile-failed"))
				.andExpect(jsonPath("$.status").value(422));
	}

	@Test
	void carriesTheGuestbookRateLimitHeaders() throws Exception {
		given(guestbookService.create(any(), any()))
				.willReturn(GuestbookSubmissionResponse.pending(UUID.randomUUID()));

		mockMvc.perform(post("203.0.113.47", VALID_BODY))
				.andExpect(status().isAccepted())
				.andExpect(header().string("RateLimit-Limit",
						Long.toString(RateLimitPolicy.GUESTBOOK.limit())))
				.andExpect(header().string("RateLimit-Remaining",
						Long.toString(RateLimitPolicy.GUESTBOOK.limit() - 1)));
	}

	@Test
	void refusesTheThirdSubmissionInADayWith429() throws Exception {
		given(guestbookService.create(any(), any()))
				.willReturn(GuestbookSubmissionResponse.pending(UUID.randomUUID()));

		for (long attempt = 1; attempt <= RateLimitPolicy.GUESTBOOK.limit(); attempt++) {
			mockMvc.perform(post("203.0.113.48", VALID_BODY)).andExpect(status().isAccepted());
		}

		mockMvc.perform(post("203.0.113.48", VALID_BODY))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().exists("Retry-After"))
				.andExpect(header().string("RateLimit-Remaining", "0"))
				.andExpect(jsonPath("$.type").value(PROBLEM_BASE + "rate-limited"));
	}

	@Test
	void countsTheSubmissionBudgetSeparatelyFromReads() throws Exception {
		given(guestbookService.create(any(), any()))
				.willReturn(GuestbookSubmissionResponse.pending(UUID.randomUUID()));
		given(guestbookService.listApproved(any(), anyInt()))
				.willReturn(new GuestbookPageResponse(List.of(), null));

		for (long attempt = 1; attempt <= RateLimitPolicy.GUESTBOOK.limit(); attempt++) {
			mockMvc.perform(post("203.0.113.49", VALID_BODY)).andExpect(status().isAccepted());
		}
		mockMvc.perform(post("203.0.113.49", VALID_BODY)).andExpect(status().isTooManyRequests());

		// Spending the two-a-day submission budget must not stop the same visitor reading.
		mockMvc.perform(get("203.0.113.49", PATH)).andExpect(status().isOk());
	}

	@Test
	void dropsAHoneypotSubmissionAsFarAsTheClientCanTell() throws Exception {
		// The controller passes the honeypot through untouched; the service decides. Asserted here
		// so a 400 is never introduced for it, which would tell a bot it had been spotted.
		given(guestbookService.create(any(), any()))
				.willReturn(GuestbookSubmissionResponse.pending(UUID.randomUUID()));

		mockMvc.perform(post("203.0.113.50", """
				{
				  "name": "Spam Bot",
				  "message": "Buy our excellent product today, friend.",
				  "turnstileToken": "token-from-the-widget",
				  "website": "https://spam.example"
				}"""))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.status").value("pending"));

		ArgumentCaptor<CreateGuestbookEntryRequest> request = ArgumentCaptor
				.forClass(CreateGuestbookEntryRequest.class);
		then(guestbookService).should().create(request.capture(), eq("203.0.113.50"));
		assertThat(request.getValue().isHoneypotTripped()).isTrue();
	}

	/** A GET that appears to come from {@code ip}, so it gets its own rate-limit bucket. */
	private static MockHttpServletRequestBuilder get(String ip, String uri) {
		return MockMvcRequestBuilders.get(uri).with(fromAddress(ip));
	}

	/** A POST that appears to come from {@code ip}, so it gets its own rate-limit bucket. */
	private static MockHttpServletRequestBuilder post(String ip, String body) {
		return MockMvcRequestBuilders.post(PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body)
				.with(fromAddress(ip));
	}

	private static RequestPostProcessor fromAddress(String ip) {
		return request -> {
			request.setRemoteAddr(ip);
			return request;
		};
	}

}
