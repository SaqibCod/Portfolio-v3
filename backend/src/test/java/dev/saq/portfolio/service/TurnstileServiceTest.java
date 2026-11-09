package dev.saq.portfolio.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dev.saq.portfolio.client.TurnstileClient;
import dev.saq.portfolio.exception.TurnstileFailedException;
import dev.saq.portfolio.exception.UpstreamUnavailableException;
import dev.saq.testsupport.TestAppProperties;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Turnstile verification against a stubbed Cloudflare. */
class TurnstileServiceTest {

	private static final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

	private MockRestServiceServer cloudflare;

	private TurnstileService turnstileService;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		cloudflare = MockRestServiceServer.bindTo(builder).build();
		turnstileService = new TurnstileService(new TurnstileClient(builder, TestAppProperties.sample()));
	}

	@Test
	void acceptsAValidToken() {
		cloudflare.expect(requestTo(VERIFY_URL))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
				.andRespond(withSuccess("""
						{"success": true, "challenge_ts": "2026-11-09T10:00:00Z", "hostname": "example.com"}""",
						MediaType.APPLICATION_JSON));

		assertThatCode(() -> turnstileService.verify("a-valid-token", "203.0.113.7"))
				.doesNotThrowAnyException();
		cloudflare.verify();
	}

	@Test
	void sendsTheSecretTokenAndCallerIp() {
		cloudflare.expect(requestTo(VERIFY_URL))
				.andExpect(content().string(Matchers.allOf(
						Matchers.containsString("secret=" + TestAppProperties.TURNSTILE_SECRET),
						Matchers.containsString("response=a-valid-token"),
						Matchers.containsString("remoteip=203.0.113.7"))))
				.andRespond(withSuccess("""
						{"success": true}""", MediaType.APPLICATION_JSON));

		turnstileService.verify("a-valid-token", "203.0.113.7");

		cloudflare.verify();
	}

	@Test
	void omitsTheCallerIpWhenUnknown() {
		cloudflare.expect(requestTo(VERIFY_URL))
				.andExpect(content().string(Matchers.not(Matchers.containsString("remoteip"))))
				.andRespond(withSuccess("""
						{"success": true}""", MediaType.APPLICATION_JSON));

		turnstileService.verify("a-valid-token", null);

		cloudflare.verify();
	}

	@Test
	void rejectsAnInvalidToken() {
		cloudflare.expect(requestTo(VERIFY_URL))
				.andRespond(withSuccess("""
						{"success": false, "error-codes": ["invalid-input-response"]}""",
						MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> turnstileService.verify("a-bad-token", "203.0.113.7"))
				.isInstanceOf(TurnstileFailedException.class)
				.hasMessageContaining("Turnstile verification failed");
		cloudflare.verify();
	}

	@Test
	void rejectsAMissingToken() {
		assertThatThrownBy(() -> turnstileService.verify(null, "203.0.113.7"))
				.isInstanceOf(TurnstileFailedException.class);
		assertThatThrownBy(() -> turnstileService.verify("   ", "203.0.113.7"))
				.isInstanceOf(TurnstileFailedException.class);

		// Cloudflare is never called for a token that cannot possibly be valid.
		cloudflare.verify();
	}

	@Test
	void reportsCloudflareOutageAsUpstreamUnavailable() {
		cloudflare.expect(requestTo(VERIFY_URL)).andRespond(withServerError());

		assertThatThrownBy(() -> turnstileService.verify("a-valid-token", "203.0.113.7"))
				.isInstanceOf(UpstreamUnavailableException.class);
		cloudflare.verify();
	}

}
