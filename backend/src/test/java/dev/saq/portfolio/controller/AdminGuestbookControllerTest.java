package dev.saq.portfolio.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.saq.portfolio.dto.response.AdminGuestbookEntryResponse;
import dev.saq.portfolio.dto.response.AdminGuestbookPageResponse;
import dev.saq.portfolio.exception.InvalidCursorException;
import dev.saq.portfolio.exception.NotFoundException;
import dev.saq.portfolio.model.GuestbookStatus;
import dev.saq.portfolio.service.GuestbookService;
import dev.saq.testsupport.TestAppProperties;
import dev.saq.testsupport.WebInfrastructureTestConfig;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The owner-only endpoints: the token gate, the status filter, and the three moderation actions.
 *
 * <p>No per-test remote addresses here, unlike the public controller — none of these handlers is
 * {@code @RateLimited}, because the only caller is the owner and a budget would just be something
 * to hit while clearing a backlog.
 */
@WebMvcTest(AdminGuestbookController.class)
@Import(WebInfrastructureTestConfig.class)
@ActiveProfiles("test")
class AdminGuestbookControllerTest {

	private static final String PROBLEM_BASE = "https://api.example.com/problems/";

	private static final String PATH = "/api/v1/admin/guestbook";

	private static final String BEARER = "Bearer " + TestAppProperties.ADMIN_TOKEN;

	private static final UUID ENTRY_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private GuestbookService guestbookService;

	// --- the token gate -----------------------------------------------------------------------

	@Test
	void refusesTheListWithoutAToken() throws Exception {
		mockMvc.perform(get(PATH))
				.andExpect(status().isUnauthorized())
				// Compatible-with rather than equal-to: the filter writes the problem body itself,
				// before Spring MVC, and sets an explicit charset on it.
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.type").value(PROBLEM_BASE + "unauthorized"))
				.andExpect(jsonPath("$.title").value("Unauthorized"))
				.andExpect(jsonPath("$.status").value(401));

		verifyNoInteractions(guestbookService);
	}

	@Test
	void refusesTheListWithTheWrongToken() throws Exception {
		mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer not-the-right-token"))
				.andExpect(status().isUnauthorized());

		verifyNoInteractions(guestbookService);
	}

	@Test
	void refusesModerationWithoutAToken() throws Exception {
		mockMvc.perform(patch(PATH + "/" + ENTRY_ID)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"status": "approved"}"""))
				.andExpect(status().isUnauthorized());

		verifyNoInteractions(guestbookService);
	}

	@Test
	void refusesDeletionWithoutAToken() throws Exception {
		mockMvc.perform(delete(PATH + "/" + ENTRY_ID)).andExpect(status().isUnauthorized());

		verifyNoInteractions(guestbookService);
	}

	// --- GET ----------------------------------------------------------------------------------

	@Test
	void servesAPageOfEntriesWithTheirStatus() throws Exception {
		given(guestbookService.listByStatus(any(), any(), anyInt()))
				.willReturn(new AdminGuestbookPageResponse(List.of(new AdminGuestbookEntryResponse(
						ENTRY_ID, "Priya", "Loved it!", Instant.parse("2026-11-20T10:15:00Z"),
						"pending", null)), "next-cursor"));

		mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, BEARER))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].id").value(ENTRY_ID.toString()))
				.andExpect(jsonPath("$.items[0].name").value("Priya"))
				.andExpect(jsonPath("$.items[0].message").value("Loved it!"))
				.andExpect(jsonPath("$.items[0].createdAt").value("2026-11-20T10:15:00Z"))
				.andExpect(jsonPath("$.items[0].status").value("pending"))
				.andExpect(jsonPath("$.items[0].moderatedAt").isEmpty())
				.andExpect(jsonPath("$.nextCursor").value("next-cursor"));
	}

	@Test
	void neverExposesTheHashedIpToTheOwnerEither() throws Exception {
		given(guestbookService.listByStatus(any(), any(), anyInt()))
				.willReturn(new AdminGuestbookPageResponse(List.of(new AdminGuestbookEntryResponse(
						ENTRY_ID, "Priya", "Loved it!", Instant.now(), "pending", null)), null));

		mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, BEARER))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].ipHash").doesNotExist());
	}

	@Test
	void defaultsToThePendingQueue() throws Exception {
		givenAnEmptyPage();

		mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, BEARER))
				.andExpect(status().isOk());

		then(guestbookService).should().listByStatus(GuestbookStatus.PENDING, null, 20);
	}

	@Test
	void acceptsEachStatusTheContractAllows() throws Exception {
		givenAnEmptyPage();

		for (String status : List.of("pending", "approved", "rejected")) {
			mockMvc.perform(get(PATH + "?status=" + status)
					.header(HttpHeaders.AUTHORIZATION, BEARER))
					.andExpect(status().isOk());
		}

		then(guestbookService).should().listByStatus(eq(GuestbookStatus.PENDING), any(), anyInt());
		then(guestbookService).should().listByStatus(eq(GuestbookStatus.APPROVED), any(), anyInt());
		then(guestbookService).should().listByStatus(eq(GuestbookStatus.REJECTED), any(), anyInt());
	}

	@Test
	void rejectsAStatusThatIsNotOneOfTheThree() throws Exception {
		mockMvc.perform(get(PATH + "?status=queued").header(HttpHeaders.AUTHORIZATION, BEARER))
				.andExpect(status().isBadRequest())
				.andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
				.andExpect(jsonPath("$.type").value(PROBLEM_BASE + "validation"))
				.andExpect(jsonPath("$.status").value(400));

		then(guestbookService).should(never()).listByStatus(any(), any(), anyInt());
	}

	@Test
	void passesTheCursorAndLimitStraightThrough() throws Exception {
		givenAnEmptyPage();

		mockMvc.perform(get(PATH + "?status=approved&cursor=abc123&limit=5")
				.header(HttpHeaders.AUTHORIZATION, BEARER))
				.andExpect(status().isOk());

		then(guestbookService).should().listByStatus(GuestbookStatus.APPROVED, "abc123", 5);
	}

	@Test
	void rejectsALimitOutsideTheContractsBounds() throws Exception {
		mockMvc.perform(get(PATH + "?limit=51").header(HttpHeaders.AUTHORIZATION, BEARER))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value(PROBLEM_BASE + "validation"));

		mockMvc.perform(get(PATH + "?limit=0").header(HttpHeaders.AUTHORIZATION, BEARER))
				.andExpect(status().isBadRequest());

		then(guestbookService).should(never()).listByStatus(any(), any(), anyInt());
	}

	@Test
	void surfacesAnUndecodableCursorAs400() throws Exception {
		willThrow(new InvalidCursorException("Cursor is malformed."))
				.given(guestbookService).listByStatus(any(), anyString(), anyInt());

		mockMvc.perform(get(PATH + "?cursor=not-a-cursor").header(HttpHeaders.AUTHORIZATION, BEARER))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("cursor"));
	}

	@Test
	void carriesNoRateLimitHeadersOnAnOwnerOnlyEndpoint() throws Exception {
		givenAnEmptyPage();

		mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, BEARER))
				.andExpect(status().isOk())
				.andExpect(header().doesNotExist("RateLimit-Limit"))
				.andExpect(header().doesNotExist("RateLimit-Remaining"));
	}

	// --- PATCH --------------------------------------------------------------------------------

	@Test
	void approvesAnEntryAndReturnsItUpdated() throws Exception {
		given(guestbookService.moderate(ENTRY_ID, GuestbookStatus.APPROVED))
				.willReturn(new AdminGuestbookEntryResponse(ENTRY_ID, "Priya", "Loved it!",
						Instant.parse("2026-11-20T10:15:00Z"), "approved",
						Instant.parse("2026-11-20T11:00:00Z")));

		mockMvc.perform(moderate("approved"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(ENTRY_ID.toString()))
				.andExpect(jsonPath("$.status").value("approved"))
				.andExpect(jsonPath("$.moderatedAt").value("2026-11-20T11:00:00Z"));
	}

	@Test
	void rejectsAnEntry() throws Exception {
		given(guestbookService.moderate(ENTRY_ID, GuestbookStatus.REJECTED))
				.willReturn(new AdminGuestbookEntryResponse(ENTRY_ID, "Priya", "Spam.",
						Instant.now(), "rejected", Instant.now()));

		mockMvc.perform(moderate("rejected"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("rejected"));

		then(guestbookService).should().moderate(ENTRY_ID, GuestbookStatus.REJECTED);
	}

	@Test
	void refusesToSetAnEntryBackToPending() throws Exception {
		// The contract's enum for this endpoint is [approved, rejected]: moderation is a decision,
		// and "un-decide this" is not one of the two.
		mockMvc.perform(moderate("pending"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value(PROBLEM_BASE + "validation"))
				.andExpect(jsonPath("$.errors[?(@.field == 'status')]").exists());

		then(guestbookService).should(never()).moderate(any(), any());
	}

	@Test
	void rejectsAStatusTheContractDoesNotDefine() throws Exception {
		mockMvc.perform(moderate("deleted"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[?(@.field == 'status')]").exists());

		then(guestbookService).should(never()).moderate(any(), any());
	}

	@Test
	void rejectsAModerationWithNoStatusAtAll() throws Exception {
		mockMvc.perform(patch(PATH + "/" + ENTRY_ID)
				.header(HttpHeaders.AUTHORIZATION, BEARER)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[?(@.field == 'status')]").exists());
	}

	@Test
	void rejectsAnEntryIdThatIsNotAUuid() throws Exception {
		mockMvc.perform(patch(PATH + "/not-a-uuid")
				.header(HttpHeaders.AUTHORIZATION, BEARER)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"status": "approved"}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value(PROBLEM_BASE + "validation"));

		then(guestbookService).should(never()).moderate(any(), any());
	}

	@Test
	void reportsModeratingAMissingEntryAs404() throws Exception {
		willThrow(new NotFoundException("No such guestbook entry."))
				.given(guestbookService).moderate(any(), any());

		mockMvc.perform(moderate("approved"))
				.andExpect(status().isNotFound())
				.andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
				.andExpect(jsonPath("$.type").value(PROBLEM_BASE + "not-found"))
				.andExpect(jsonPath("$.status").value(404));
	}

	// --- DELETE -------------------------------------------------------------------------------

	@Test
	void deletesAnEntryWith204AndNoBody() throws Exception {
		willDoNothing().given(guestbookService).delete(ENTRY_ID);

		mockMvc.perform(delete(PATH + "/" + ENTRY_ID).header(HttpHeaders.AUTHORIZATION, BEARER))
				.andExpect(status().isNoContent())
				.andExpect(content -> assertThat(content.getResponse().getContentAsString()).isEmpty());

		then(guestbookService).should().delete(ENTRY_ID);
	}

	@Test
	void reportsDeletingAMissingEntryAs404() throws Exception {
		willThrow(new NotFoundException("No such guestbook entry."))
				.given(guestbookService).delete(any());

		mockMvc.perform(delete(PATH + "/" + ENTRY_ID).header(HttpHeaders.AUTHORIZATION, BEARER))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.type").value(PROBLEM_BASE + "not-found"));
	}

	private void givenAnEmptyPage() {
		given(guestbookService.listByStatus(any(), any(), anyInt()))
				.willReturn(new AdminGuestbookPageResponse(List.of(), null));
	}

	private static MockHttpServletRequestBuilder moderate(String status) {
		return patch(PATH + "/" + ENTRY_ID)
				.header(HttpHeaders.AUTHORIZATION, BEARER)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"status": "%s"}""".formatted(status));
	}

}
