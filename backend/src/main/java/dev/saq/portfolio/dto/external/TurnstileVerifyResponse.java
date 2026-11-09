package dev.saq.portfolio.dto.external;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Cloudflare's {@code siteverify} response.
 *
 * <p>Third-party shape, so the field names are theirs, not ours. Unknown properties are ignored
 * on purpose: Cloudflare may add fields at any time and that must not break verification.
 *
 * @param success    whether the token was valid
 * @param challengeTs when the challenge was solved
 * @param hostname   the site the challenge was solved on
 * @param errorCodes Cloudflare's error codes, empty on success
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TurnstileVerifyResponse(
		boolean success,
		@JsonProperty("challenge_ts") String challengeTs,
		String hostname,
		@JsonProperty("error-codes") List<String> errorCodes) {

	/** Never null, so callers can log the codes without a guard. */
	@Override
	public List<String> errorCodes() {
		return (errorCodes != null) ? errorCodes : List.of();
	}

}
