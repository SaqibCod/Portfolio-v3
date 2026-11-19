package dev.saq.portfolio.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import dev.saq.testsupport.TestAppProperties;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpUtilsTest {

	private static final String SALT = TestAppProperties.IP_SALT;

	private final ClientIpUtils clientIpUtils = new ClientIpUtils(TestAppProperties.sample());

	@Test
	void resolvesTheRemoteAddress() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("203.0.113.7");

		assertThat(clientIpUtils.resolve(request)).isEqualTo("203.0.113.7");
	}

	@Test
	void fallsBackWhenNoAddressIsAvailable() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr(null);

		assertThat(clientIpUtils.resolve(request)).isEqualTo("unknown");
		assertThat(clientIpUtils.hashOf(request)).isEqualTo(clientIpUtils.hash("unknown"));
	}

	@Test
	void resolveOrNullReportsAnAddressItHas() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("203.0.113.7");

		assertThat(clientIpUtils.resolveOrNull(request)).isEqualTo("203.0.113.7");
	}

	@Test
	void resolveOrNullReportsAbsenceRatherThanTheSentinel() {
		// Callers that pass the address to a third party need "no address", not the string
		// "unknown", which would be an invalid value rather than an omitted one.
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr(null);

		assertThat(clientIpUtils.resolveOrNull(request)).isNull();
	}

	@Test
	void aBlankAddressCountsAsNoAddress() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("   ");

		assertThat(clientIpUtils.resolveOrNull(request)).isNull();
		assertThat(clientIpUtils.resolve(request)).isEqualTo("unknown");
	}

	@Test
	void hashFoldsAnAbsentAddressOntoTheSentinel() {
		assertThat(clientIpUtils.hash(null)).isEqualTo(clientIpUtils.hash("unknown"));
	}

	@Test
	void hashIsSaltedSha256Hex() throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		digest.update(SALT.getBytes(StandardCharsets.UTF_8));
		digest.update("203.0.113.7".getBytes(StandardCharsets.UTF_8));
		String expected = HexFormat.of().formatHex(digest.digest());

		assertThat(clientIpUtils.hash("203.0.113.7"))
				.isEqualTo(expected)
				.hasSize(64)
				.matches("[0-9a-f]{64}");
	}

	@Test
	void hashIsStableForTheSameAddress() {
		assertThat(clientIpUtils.hash("198.51.100.4")).isEqualTo(clientIpUtils.hash("198.51.100.4"));
	}

	@Test
	void differentAddressesHashDifferently() {
		assertThat(clientIpUtils.hash("198.51.100.4")).isNotEqualTo(clientIpUtils.hash("198.51.100.5"));
	}

	@Test
	void hashNeverContainsTheRawAddress() {
		assertThat(clientIpUtils.hash("198.51.100.4")).doesNotContain("198.51.100.4");
	}

	@Test
	void differentSaltsProduceDifferentHashes() {
		ClientIpUtils other = new ClientIpUtils(TestAppProperties.withIpSalt("a-completely-different-salt"));

		assertThat(clientIpUtils.hash("198.51.100.4")).isNotEqualTo(other.hash("198.51.100.4"));
	}

	@Test
	void hashesTheResolvedAddressOfARequest() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("203.0.113.7");

		assertThat(clientIpUtils.hashOf(request)).isEqualTo(clientIpUtils.hash("203.0.113.7"));
	}

}
