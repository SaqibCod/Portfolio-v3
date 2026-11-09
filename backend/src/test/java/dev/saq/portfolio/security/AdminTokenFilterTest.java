package dev.saq.portfolio.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.saq.testsupport.TestAppProperties;
import dev.saq.testsupport.TestEndpointsConfig;
import dev.saq.testsupport.WebInfrastructureTestConfig;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** {@code /api/v1/admin/**} is reachable with the owner's token and with nothing else. */
@WebMvcTest
@Import({ TestEndpointsConfig.class, WebInfrastructureTestConfig.class })
@ActiveProfiles("test")
class AdminTokenFilterTest {

	private static final String ADMIN_PATH = "/api/v1/admin/test/ping";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void reachesTheHandlerWithTheRightToken() throws Exception {
		mockMvc.perform(get(ADMIN_PATH).header("Authorization", "Bearer " + TestAppProperties.ADMIN_TOKEN))
				.andExpect(status().isOk())
				.andExpect(content().string("admin-pong"));
	}

	@Test
	void rejectsAMissingAuthorizationHeader() throws Exception {
		mockMvc.perform(get(ADMIN_PATH))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
				.andExpect(jsonPath("$.type").value("https://api.example.com/problems/unauthorized"))
				.andExpect(jsonPath("$.title").value("Unauthorized"))
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.detail").value("A valid admin token is required."));
	}

	@Test
	void rejectsAWrongToken() throws Exception {
		mockMvc.perform(get(ADMIN_PATH).header("Authorization", "Bearer not-the-right-token"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.type").value("https://api.example.com/problems/unauthorized"));
	}

	@Test
	void rejectsATokenThatIsAPrefixOfTheRealOne() throws Exception {
		String prefix = TestAppProperties.ADMIN_TOKEN.substring(0, TestAppProperties.ADMIN_TOKEN.length() - 1);

		mockMvc.perform(get(ADMIN_PATH).header("Authorization", "Bearer " + prefix))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void rejectsANonBearerScheme() throws Exception {
		mockMvc.perform(get(ADMIN_PATH).header("Authorization", TestAppProperties.ADMIN_TOKEN))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get(ADMIN_PATH).header("Authorization", "Basic " + TestAppProperties.ADMIN_TOKEN))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void neverEchoesTheExpectedToken() throws Exception {
		mockMvc.perform(get(ADMIN_PATH))
				.andExpect(content().string(
						Matchers.not(Matchers.containsString(TestAppProperties.ADMIN_TOKEN))));
	}

	@Test
	void leavesPublicEndpointsOpen() throws Exception {
		mockMvc.perform(get("/api/v1/test/ok"))
				.andExpect(status().isOk())
				.andExpect(content().string("ok"));
	}

}
