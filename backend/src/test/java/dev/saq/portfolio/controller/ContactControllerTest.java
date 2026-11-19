package dev.saq.portfolio.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import dev.saq.portfolio.dto.request.ContactRequest;
import dev.saq.portfolio.dto.response.ContactAcceptedResponse;
import dev.saq.portfolio.exception.TurnstileFailedException;
import dev.saq.portfolio.model.RateLimitPolicy;
import dev.saq.portfolio.service.ContactService;
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

/**
 * HTTP in, HTTP out: status codes, the 202 body, validation problems and the rate-limit headers,
 * with the service mocked.
 *
 * <p>Every test posts from its own remote address. The rate-limit interceptor is live in this
 * slice and buckets are keyed by hashed IP, so distinct addresses keep the tests independent
 * despite the shared application context — a test that reused one would be spending another
 * test's three-per-hour budget.
 */
@WebMvcTest(ContactController.class)
@Import(WebInfrastructureTestConfig.class)
@ActiveProfiles("test")
class ContactControllerTest {

	private static final String PROBLEM_BASE = "https://api.example.com/problems/";

	private static final String VALID_BODY = """
			{
			  "name": "Ada Lovelace",
			  "email": "ada@example.com",
			  "subject": "A role for you",
			  "message": "I would like to talk to you about a backend role.",
			  "turnstileToken": "token-from-the-widget"
			}""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ContactService contactService;

	@Test
	void acceptsAValidSubmissionWith202() throws Exception {
		UUID id = UUID.fromString("11111111-2222-3333-4444-555555555555");
		given(contactService.submit(any(), any())).willReturn(ContactAcceptedResponse.queued(id));

		mockMvc.perform(post("203.0.113.1", VALID_BODY))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.id").value(id.toString()))
				.andExpect(jsonPath("$.status").value("queued"));
	}

	@Test
	void passesTheParsedRequestAndCallerAddressToTheService() throws Exception {
		given(contactService.submit(any(), any()))
				.willReturn(ContactAcceptedResponse.queued(UUID.randomUUID()));

		mockMvc.perform(post("203.0.113.2", VALID_BODY)).andExpect(status().isAccepted());

		ArgumentCaptor<ContactRequest> request = ArgumentCaptor.forClass(ContactRequest.class);
		ArgumentCaptor<String> ip = ArgumentCaptor.forClass(String.class);
		then(contactService).should().submit(request.capture(), ip.capture());

		assertThat(request.getValue().name()).isEqualTo("Ada Lovelace");
		assertThat(request.getValue().email()).isEqualTo("ada@example.com");
		assertThat(request.getValue().turnstileToken()).isEqualTo("token-from-the-widget");
		assertThat(ip.getValue()).isEqualTo("203.0.113.2");
	}

	@Test
	void reportsEveryInvalidFieldAsA400() throws Exception {
		String body = """
				{
				  "name": "",
				  "email": "not-an-email",
				  "message": "too short",
				  "turnstileToken": ""
				}""";

		mockMvc.perform(post("203.0.113.3", body))
				.andExpect(status().isBadRequest())
				.andExpect(header().string("Content-Type",
						MediaType.APPLICATION_PROBLEM_JSON_VALUE))
				.andExpect(jsonPath("$.type").value(PROBLEM_BASE + "validation"))
				.andExpect(jsonPath("$.title").value("Validation failed"))
				.andExpect(jsonPath("$.errors[?(@.field == 'name')]").exists())
				.andExpect(jsonPath("$.errors[?(@.field == 'email')]").exists())
				.andExpect(jsonPath("$.errors[?(@.field == 'message')]").exists())
				.andExpect(jsonPath("$.errors[?(@.field == 'turnstileToken')]").exists());

		then(contactService).should(never()).submit(any(), any());
	}

	@Test
	void rejectsAMessageOverTheContractsLimit() throws Exception {
		String body = """
				{
				  "name": "Ada Lovelace",
				  "email": "ada@example.com",
				  "message": "%s",
				  "turnstileToken": "token-from-the-widget"
				}""".formatted("x".repeat(5001));

		mockMvc.perform(post("203.0.113.4", body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[?(@.field == 'message')]").exists());

		then(contactService).should(never()).submit(any(), any());
	}

	@Test
	void rejectsAMalformedBody() throws Exception {
		mockMvc.perform(post("203.0.113.5", "{not json"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value(PROBLEM_BASE + "validation"))
				.andExpect(jsonPath("$.errors[0].field").value("body"));
	}

	@Test
	void surfacesATurnstileRejectionAs422() throws Exception {
		willThrow(new TurnstileFailedException("Turnstile verification failed."))
				.given(contactService).submit(any(), any());

		mockMvc.perform(post("203.0.113.6", VALID_BODY))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.type").value(PROBLEM_BASE + "turnstile-failed"))
				.andExpect(jsonPath("$.status").value(422));
	}

	@Test
	void carriesTheContactRateLimitHeaders() throws Exception {
		given(contactService.submit(any(), any()))
				.willReturn(ContactAcceptedResponse.queued(UUID.randomUUID()));

		mockMvc.perform(post("203.0.113.7", VALID_BODY))
				.andExpect(status().isAccepted())
				.andExpect(header().string("RateLimit-Limit",
						Long.toString(RateLimitPolicy.CONTACT.limit())))
				.andExpect(header().string("RateLimit-Remaining",
						Long.toString(RateLimitPolicy.CONTACT.limit() - 1)));
	}

	@Test
	void refusesTheFourthSubmissionInAnHourWith429() throws Exception {
		given(contactService.submit(any(), any()))
				.willReturn(ContactAcceptedResponse.queued(UUID.randomUUID()));

		for (int attempt = 1; attempt <= RateLimitPolicy.CONTACT.limit(); attempt++) {
			mockMvc.perform(post("203.0.113.8", VALID_BODY)).andExpect(status().isAccepted());
		}

		mockMvc.perform(post("203.0.113.8", VALID_BODY))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().exists("Retry-After"))
				.andExpect(header().string("RateLimit-Remaining", "0"))
				.andExpect(jsonPath("$.type").value(PROBLEM_BASE + "rate-limited"));
	}

	/** A POST that appears to come from {@code ip}, so it gets its own rate-limit bucket. */
	private static MockHttpServletRequestBuilder post(String ip, String body) {
		return MockMvcRequestBuilders.post("/api/v1/contact")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body)
				.with(request -> {
					request.setRemoteAddr(ip);
					return request;
				});
	}

}
