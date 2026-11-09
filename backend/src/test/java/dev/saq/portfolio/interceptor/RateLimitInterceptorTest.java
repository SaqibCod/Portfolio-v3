package dev.saq.portfolio.interceptor;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.saq.portfolio.model.RateLimitPolicy;
import dev.saq.testsupport.TestEndpointsConfig;
import dev.saq.testsupport.WebInfrastructureTestConfig;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * The interceptor runs inside a real MVC stack here, so the headers and the 429 are exactly what
 * a client would see.
 *
 * <p>Every test uses its own remote address: buckets are keyed by hashed IP, so distinct addresses
 * keep the tests independent despite the shared application context.
 */
@WebMvcTest
@Import({ TestEndpointsConfig.class, WebInfrastructureTestConfig.class })
@ActiveProfiles("test")
class RateLimitInterceptorTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void addsRateLimitHeadersToAnAllowedRequest() throws Exception {
		mockMvc.perform(from("198.51.100.1").get("/api/v1/test/limited-read"))
				.andExpect(status().isOk())
				.andExpect(header().string("RateLimit-Limit",
						Long.toString(RateLimitPolicy.PUBLIC_READ.limit())))
				.andExpect(header().string("RateLimit-Remaining",
						Long.toString(RateLimitPolicy.PUBLIC_READ.limit() - 1)));
	}

	@Test
	void countsRemainingDownAcrossRequests() throws Exception {
		Requests client = from("198.51.100.2");

		mockMvc.perform(client.get("/api/v1/test/limited-read"))
				.andExpect(header().string("RateLimit-Remaining", "119"));
		mockMvc.perform(client.get("/api/v1/test/limited-read"))
				.andExpect(header().string("RateLimit-Remaining", "118"));
	}

	@Test
	void returns429WithRetryAfterOnceThePolicyIsExhausted() throws Exception {
		Requests client = from("198.51.100.3");

		// GUESTBOOK allows 2 per day.
		mockMvc.perform(client.get("/api/v1/test/limited")).andExpect(status().isOk());
		mockMvc.perform(client.get("/api/v1/test/limited"))
				.andExpect(status().isOk())
				.andExpect(header().string("RateLimit-Remaining", "0"));

		mockMvc.perform(client.get("/api/v1/test/limited"))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().exists("Retry-After"))
				.andExpect(header().string("RateLimit-Limit", "2"))
				.andExpect(header().string("RateLimit-Remaining", "0"))
				.andExpect(jsonPath("$.type").value("https://api.example.com/problems/rate-limited"))
				.andExpect(jsonPath("$.title").value("Too many requests"))
				.andExpect(jsonPath("$.status").value(429));
	}

	@Test
	void budgetsAreSeparatePerCaller() throws Exception {
		Requests first = from("198.51.100.4");
		Requests second = from("198.51.100.5");

		mockMvc.perform(first.get("/api/v1/test/limited")).andExpect(status().isOk());
		mockMvc.perform(first.get("/api/v1/test/limited")).andExpect(status().isOk());
		mockMvc.perform(first.get("/api/v1/test/limited")).andExpect(status().isTooManyRequests());

		mockMvc.perform(second.get("/api/v1/test/limited"))
				.andExpect(status().isOk())
				.andExpect(header().string("RateLimit-Remaining", "1"));
	}

	@Test
	void leavesHandlersWithoutThePolicyAlone() throws Exception {
		mockMvc.perform(from("198.51.100.6").get("/api/v1/test/ok"))
				.andExpect(status().isOk())
				.andExpect(header().doesNotExist("RateLimit-Limit"))
				.andExpect(header().doesNotExist("RateLimit-Remaining"));
	}

	private static Requests from(String ip) {
		return new Requests(ip);
	}

	/** Builds requests that all appear to come from one client address. */
	private record Requests(String ip) {

		MockHttpServletRequestBuilder get(String path) {
			return MockMvcRequestBuilders.get(path).with(request -> {
				request.setRemoteAddr(ip);
				return request;
			});
		}

	}

}
