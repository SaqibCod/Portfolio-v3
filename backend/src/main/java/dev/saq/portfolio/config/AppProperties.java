package dev.saq.portfolio.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Every application-specific setting, bound from {@code app.*}.
 *
 * <p>The tree is validated eagerly, so the application refuses to start when a required
 * value is missing instead of failing on the first request that needs it.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
		@NotNull @Valid Api api,
		@NotNull @Valid Cors cors,
		@NotNull @Valid Security security,
		@NotNull @Valid Turnstile turnstile,
		@NotNull @Valid Mail mail) {

	/**
	 * Identity of this API on the public internet.
	 *
	 * @param baseUrl the scheme and host clients reach the API on, with no trailing slash. Used to
	 *                build the RFC 9457 problem {@code type} URIs, which are published identifiers:
	 *                changing this value changes what clients match on.
	 */
	public record Api(
			@NotBlank @Pattern(regexp = "https?://.+", message = "must be an absolute http(s) URL")
			String baseUrl) {
	}

	/** CORS settings for the browser clients that may call this API. */
	public record Cors(
			@NotEmpty List<@NotBlank String> allowedOrigins) {
	}

	/**
	 * Secrets used to protect the API.
	 *
	 * @param ipSalt     salt mixed into every client IP hash; raw IPs are never stored
	 * @param adminToken bearer token for {@code /api/v1/admin/**}
	 */
	public record Security(
			@NotBlank @Size(min = 16) String ipSalt,
			@NotBlank @Size(min = 32) String adminToken) {
	}

	/** Cloudflare Turnstile server-side verification. */
	public record Turnstile(
			@NotBlank String secretKey) {
	}

	/**
	 * Outbound email through Resend.
	 *
	 * @param resendApiKey Resend API key
	 * @param from         verified sender address
	 * @param ownerEmail   where contact-form notifications are delivered
	 */
	public record Mail(
			@NotBlank String resendApiKey,
			@NotBlank @Email String from,
			@NotBlank @Email String ownerEmail) {
	}

}
