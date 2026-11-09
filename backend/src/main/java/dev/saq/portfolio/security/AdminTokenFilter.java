package dev.saq.portfolio.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import dev.saq.portfolio.config.AppProperties;
import dev.saq.portfolio.exception.ProblemTypes;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Guards {@code /api/v1/admin/**} with a single bearer token.
 *
 * <p>Visitors never sign in, so there are no sessions, users or CSRF concerns — only the owner's
 * secret. The comparison uses {@link MessageDigest#isEqual} so a wrong token cannot be recovered
 * by timing the response.
 */
@Component
public class AdminTokenFilter extends OncePerRequestFilter {

	private static final String ADMIN_PATH_PREFIX = "/api/v1/admin/";

	private static final String BEARER_PREFIX = "Bearer ";

	private final byte[] expectedToken;

	private final String unauthorizedBody;

	public AdminTokenFilter(AppProperties properties, ProblemTypes problemTypes) {
		this.expectedToken = properties.security().adminToken().getBytes(StandardCharsets.UTF_8);
		// Built once: the body is the same for every rejection.
		this.unauthorizedBody = """
				{"type":"%s","title":"Unauthorized","status":401,\
				"detail":"A valid admin token is required."}""".formatted(problemTypes.unauthorized());
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();
		return path == null || !path.startsWith(ADMIN_PATH_PREFIX);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {

		if (!hasValidToken(request)) {
			writeUnauthorized(response, unauthorizedBody);
			return;
		}
		filterChain.doFilter(request, response);
	}

	private boolean hasValidToken(HttpServletRequest request) {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header == null || !header.startsWith(BEARER_PREFIX)) {
			return false;
		}
		byte[] presented = header.substring(BEARER_PREFIX.length()).getBytes(StandardCharsets.UTF_8);
		return MessageDigest.isEqual(expectedToken, presented);
	}

	/**
	 * Writes the problem response directly: the filter runs before Spring MVC, so
	 * {@code GlobalExceptionHandler} cannot do it here. The body is byte-identical in shape to
	 * every other 401 the API produces.
	 */
	private static void writeUnauthorized(HttpServletResponse response, String body) throws IOException {
		response.setStatus(HttpStatus.UNAUTHORIZED.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write(body);
	}

}
