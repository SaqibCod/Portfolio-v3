package dev.saq.portfolio.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.saq.testsupport.TestEndpointsConfig;
import dev.saq.testsupport.WebInfrastructureTestConfig;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Changing {@code app.api.base-url} (in deployment: the {@code API_BASE_URL} environment variable)
 * must change the {@code type} URIs clients actually receive, with the
 * {@code /problems/{slug}} structure untouched. This is what makes swapping the placeholder domain
 * a configuration change rather than a code change.
 */
@WebMvcTest
@Import({ TestEndpointsConfig.class, WebInfrastructureTestConfig.class })
@ActiveProfiles("test")
@TestPropertySource(properties = "app.api.base-url=https://api.saq.dev")
class ProblemTypeBaseUrlOverrideTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void handledExceptionsUseTheOverriddenBase() throws Exception {
		mockMvc.perform(get("/api/v1/test/not-found"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.type").value("https://api.saq.dev/problems/not-found"));
	}

	@Test
	void rateLimitProblemsUseTheOverriddenBase() throws Exception {
		mockMvc.perform(get("/api/v1/test/rate-limited"))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.type").value("https://api.saq.dev/problems/rate-limited"));
	}

	@Test
	void springsOwnExceptionsUseTheOverriddenBase() throws Exception {
		mockMvc.perform(get("/api/v1/test/no-such-route"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.type").value("https://api.saq.dev/problems/not-found"));
	}

	@Test
	void theAdminFilterUsesTheOverriddenBase() throws Exception {
		mockMvc.perform(get("/api/v1/admin/test/ping"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.type").value("https://api.saq.dev/problems/unauthorized"));
	}

}
