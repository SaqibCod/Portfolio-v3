package dev.saq.portfolio.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.saq.testsupport.TestEndpointsConfig;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * One case per row of the error-mapping table in {@code docs/plan/phase-01-backend-core.md}.
 * Every response must be {@code application/problem+json} and carry {@code type}, {@code title}
 * and {@code status}, matching the contract's Problem schema.
 */
@WebMvcTest
@Import(TestEndpointsConfig.class)
@ActiveProfiles("test")
class GlobalExceptionHandlerTest {

	private static final String PROBLEM_JSON = "application/problem+json";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void invalidRequestBodyReturns400WithFieldErrors() throws Exception {
		mockMvc.perform(post("/api/v1/test/validate")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "", "message": "far too long to fit"}"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.type").value("https://api.example.com/problems/validation"))
				.andExpect(jsonPath("$.title").value("Validation failed"))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.errors", Matchers.hasSize(2)))
				.andExpect(jsonPath("$.errors[*].field",
						Matchers.containsInAnyOrder("name", "message")))
				.andExpect(jsonPath("$.errors[*].message", Matchers.everyItem(Matchers.not(Matchers.blankOrNullString()))));
	}

	@Test
	void malformedJsonReturns400() throws Exception {
		mockMvc.perform(post("/api/v1/test/validate")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ not json"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.type").value("https://api.example.com/problems/validation"))
				.andExpect(jsonPath("$.title").value("Validation failed"))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.errors[0].field").value("body"));
	}

	@Test
	void notFoundExceptionReturns404() throws Exception {
		mockMvc.perform(get("/api/v1/test/not-found"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.type").value("https://api.example.com/problems/not-found"))
				.andExpect(jsonPath("$.title").value("Not found"))
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.detail").value("No such thing."));
	}

	@Test
	void unknownRouteReturns404Problem() throws Exception {
		mockMvc.perform(get("/api/v1/test/no-such-route"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.type").value("https://api.example.com/problems/not-found"))
				.andExpect(jsonPath("$.title").value("Not found"))
				.andExpect(jsonPath("$.status").value(404));
	}

	@Test
	void turnstileFailureReturns422() throws Exception {
		mockMvc.perform(get("/api/v1/test/turnstile-failed"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.type").value("https://api.example.com/problems/turnstile-failed"))
				.andExpect(jsonPath("$.title").value("Turnstile verification failed"))
				.andExpect(jsonPath("$.status").value(422));
	}

	@Test
	void rateLimitedReturns429WithRetryAfter() throws Exception {
		mockMvc.perform(get("/api/v1/test/rate-limited"))
				.andExpect(status().isTooManyRequests())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(header().string("Retry-After", "42"))
				.andExpect(jsonPath("$.type").value("https://api.example.com/problems/rate-limited"))
				.andExpect(jsonPath("$.title").value("Too many requests"))
				.andExpect(jsonPath("$.status").value(429));
	}

	@Test
	void upstreamUnavailableReturns503WithRetryAfter() throws Exception {
		mockMvc.perform(get("/api/v1/test/upstream-unavailable"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(header().string("Retry-After", "17"))
				.andExpect(jsonPath("$.type").value("https://api.example.com/problems/upstream-unavailable"))
				.andExpect(jsonPath("$.title").value("Service unavailable"))
				.andExpect(jsonPath("$.status").value(503));
	}

	@Test
	void unexpectedExceptionReturnsGeneric500WithoutLeakingDetails() throws Exception {
		mockMvc.perform(get("/api/v1/test/boom"))
				.andExpect(status().isInternalServerError())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.type").value("https://api.example.com/problems/internal"))
				.andExpect(jsonPath("$.title").value("Internal server error"))
				.andExpect(jsonPath("$.status").value(500))
				.andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
				.andExpect(jsonPath("$.correlationId").isNotEmpty())
				.andExpect(content().string(Matchers.not(Matchers.containsString("must never be exposed"))))
				.andExpect(content().string(Matchers.not(Matchers.containsString("IllegalStateException"))));
	}

}
