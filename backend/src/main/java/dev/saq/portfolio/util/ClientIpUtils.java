package dev.saq.portfolio.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import dev.saq.portfolio.config.AppProperties;

import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the caller's IP address and turns it into a one-way hash.
 *
 * <p>Raw IP addresses are never stored or logged. Rate limiting and abuse checks only ever see
 * {@code SHA-256(salt + ip)}, which is stable for one deployment and useless outside it.
 *
 * <p>Forwarded headers are not parsed here: {@code server.forward-headers-strategy=framework}
 * makes Boot apply {@code X-Forwarded-For} before the request reaches any application code, so
 * {@code getRemoteAddr()} is already the client address behind the proxy.
 */
@Component
public class ClientIpUtils {

	private static final String UNKNOWN_IP = "unknown";

	private final String salt;

	public ClientIpUtils(AppProperties properties) {
		this.salt = properties.security().ipSalt();
	}

	/** The caller's IP address, or {@code "unknown"} when the container cannot supply one. */
	public String resolve(HttpServletRequest request) {
		String remoteAddress = request.getRemoteAddr();
		return (remoteAddress == null || remoteAddress.isBlank()) ? UNKNOWN_IP : remoteAddress;
	}

	/** Lowercase hex {@code SHA-256(salt + ip)}. */
	public String hash(String ip) {
		String value = (ip == null || ip.isBlank()) ? UNKNOWN_IP : ip;
		MessageDigest digest = sha256();
		digest.update(salt.getBytes(StandardCharsets.UTF_8));
		digest.update(value.getBytes(StandardCharsets.UTF_8));
		return HexFormat.of().formatHex(digest.digest());
	}

	/** Convenience for the common case: resolve the caller, then hash it. */
	public String hashOf(HttpServletRequest request) {
		return hash(resolve(request));
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException ex) {
			// SHA-256 is required of every JVM, so this cannot happen.
			throw new IllegalStateException("SHA-256 is not available", ex);
		}
	}

}
