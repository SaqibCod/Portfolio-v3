package dev.saq.testsupport;

import java.util.List;

import dev.saq.portfolio.config.AppProperties;

/** Fully populated {@link AppProperties} for unit tests that build components by hand. */
public final class TestAppProperties {

	/** Matches {@code application-test.yml}, so hand-built and context-bound tests agree. */
	public static final String API_BASE_URL = "https://api.example.com";

	public static final String IP_SALT = "test-ip-salt-value";

	public static final String ADMIN_TOKEN = "test-admin-token-0123456789abcdef";

	/** Cloudflare's documented "always passes" test secret key. */
	public static final String TURNSTILE_SECRET = "1x0000000000000000000000000000000AA";

	public static final String RESEND_API_KEY = "test-resend-api-key";

	public static final String MAIL_FROM = "portfolio@example.com";

	public static final String OWNER_EMAIL = "owner@example.com";

	public static AppProperties sample() {
		return builder(IP_SALT, ADMIN_TOKEN);
	}

	public static AppProperties withIpSalt(String ipSalt) {
		return builder(ipSalt, ADMIN_TOKEN);
	}

	public static AppProperties withAdminToken(String adminToken) {
		return builder(IP_SALT, adminToken);
	}

	private static AppProperties builder(String ipSalt, String adminToken) {
		return new AppProperties(
				new AppProperties.Api(API_BASE_URL),
				new AppProperties.Cors(List.of("http://localhost:3000")),
				new AppProperties.Security(ipSalt, adminToken),
				new AppProperties.Turnstile(TURNSTILE_SECRET),
				new AppProperties.Mail(RESEND_API_KEY, MAIL_FROM, OWNER_EMAIL));
	}

	private TestAppProperties() {
	}

}
