package dev.saq.portfolio.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.saq.testsupport.TestEndpointsConfig;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The API answers preflight requests from the configured origin only.
 *
 * <p>{@code @ConfigurationPropertiesScan} is not applied in an MVC slice, so {@link AppProperties}
 * is enabled explicitly here.
 */
@WebMvcTest
@Import(TestEndpointsConfig.class)
@EnableConfigurationProperties(AppProperties.class)
@ActiveProfiles("test")
class CorsConfigTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void allowsTheConfiguredOrigin() throws Exception {
		mockMvc.perform(options("/api/v1/test/ok")
				.header("Origin", "http://localhost:3000")
				.header("Access-Control-Request-Method", "GET"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
				.andExpect(header().string("Access-Control-Allow-Methods", "GET,POST,PATCH,DELETE"));
	}

	@Test
	void rejectsAnUnknownOrigin() throws Exception {
		mockMvc.perform(options("/api/v1/test/ok")
				.header("Origin", "https://evil.example")
				.header("Access-Control-Request-Method", "GET"))
				.andExpect(status().isForbidden());
	}

	@Test
	void rejectsAMethodThatIsNotAllowed() throws Exception {
		mockMvc.perform(options("/api/v1/test/ok")
				.header("Origin", "http://localhost:3000")
				.header("Access-Control-Request-Method", "PUT"))
				.andExpect(status().isForbidden());
	}

}
