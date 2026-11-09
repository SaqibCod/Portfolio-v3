package dev.saq.portfolio.service.impl;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dev.saq.portfolio.client.ResendClient;
import dev.saq.testsupport.TestAppProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Sending mail through a stubbed Resend. */
class ResendEmailServiceTest {

	private static final String EMAILS_URL = "https://api.resend.com/emails";

	private MockRestServiceServer resend;

	private ResendEmailService emailService;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		resend = MockRestServiceServer.bindTo(builder).build();
		emailService = new ResendEmailService(new ResendClient(builder, TestAppProperties.sample()),
				TestAppProperties.sample());
	}

	@Test
	void postsTheMessageToResend() {
		resend.expect(requestTo(EMAILS_URL))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("Authorization", "Bearer " + TestAppProperties.RESEND_API_KEY))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.from").value(TestAppProperties.MAIL_FROM))
				.andExpect(jsonPath("$.to[0]").value(TestAppProperties.OWNER_EMAIL))
				.andExpect(jsonPath("$.subject").value("New contact message"))
				.andExpect(jsonPath("$.text").value("Hello there."))
				.andExpect(jsonPath("$.reply_to").value("visitor@example.com"))
				.andRespond(withSuccess("""
						{"id": "0c7b5f2e-0000-0000-0000-000000000000"}""", MediaType.APPLICATION_JSON));

		emailService.send(TestAppProperties.OWNER_EMAIL, "New contact message", "Hello there.",
				"visitor@example.com");

		resend.verify();
	}

	@Test
	void omitsReplyToWhenThereIsNone() {
		resend.expect(requestTo(EMAILS_URL))
				.andExpect(jsonPath("$.reply_to").doesNotExist())
				.andRespond(withSuccess("""
						{"id": "0c7b5f2e-0000-0000-0000-000000000000"}""", MediaType.APPLICATION_JSON));

		emailService.send(TestAppProperties.OWNER_EMAIL, "New contact message", "Hello there.", null);

		resend.verify();
	}

	@Test
	void swallowsAnUpstreamFailureSoTheCallerIsNeverAffected() {
		resend.expect(requestTo(EMAILS_URL)).andRespond(withServerError());

		assertThatCode(() -> emailService.send(TestAppProperties.OWNER_EMAIL, "New contact message",
				"Hello there.", null)).doesNotThrowAnyException();

		resend.verify();
	}

}
