package dev.saq.portfolio.exception;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Turns every exception into an RFC 9457 {@code application/problem+json} response that matches
 * the {@code Problem} schema in {@code docs/api/portfolio-api.yaml}.
 *
 * <p>Stack traces and upstream messages never reach a client: unexpected failures are logged with
 * a correlation id and answered with a generic 500.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	private final ProblemTypes problemTypes;

	public GlobalExceptionHandler(ProblemTypes problemTypes) {
		this.problemTypes = problemTypes;
	}

	/** 400 — a request DTO failed bean validation; report the offending fields. */
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(
			MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

		ProblemDetail body = problem(HttpStatus.BAD_REQUEST, problemTypes.validation(),
				"Validation failed", "One or more fields are invalid.");
		body.setProperty("errors", fieldErrors(ex.getBindingResult()));
		return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
	}

	/** 400 — the body was absent, malformed or not JSON at all. */
	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(
			HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

		ProblemDetail body = problem(HttpStatus.BAD_REQUEST, problemTypes.validation(),
				"Validation failed", "Request body is missing or malformed.");
		body.setProperty("errors", List.of(error("body", "must be valid JSON matching the request schema")));
		return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
	}

	/**
	 * Built-in Spring web exceptions (unknown route, wrong method, unsupported media type) build
	 * their own {@code ProblemDetail} with {@code type} left at {@code about:blank}. This is the
	 * one place every such body passes through, so it is where our {@code type} URIs are applied.
	 */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(
			Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {

		ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
		if (response != null && response.getBody() instanceof ProblemDetail problem && isUntyped(problem)) {
			URI type = typeFor(statusCode);
			// Statuses the contract does not describe (405, 415, 406…) keep Spring's about:blank
			// type and reason phrase rather than being mislabelled as one of ours.
			if (type != null) {
				problem.setType(type);
				problem.setTitle(titleFor(statusCode));
			}
			if (ex instanceof NoResourceFoundException) {
				// Default detail mentions static resources, which means nothing to an API client.
				problem.setDetail("No such resource.");
			}
		}
		return response;
	}

	/** True while {@code type} is still RFC 9457's {@code about:blank} default. */
	private static boolean isUntyped(ProblemDetail problem) {
		URI type = problem.getType();
		return type == null || "about:blank".equals(type.toString());
	}

	/** 404 */
	@ExceptionHandler(NotFoundException.class)
	ProblemDetail handleNotFound(NotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, problemTypes.notFound(), "Not found", ex.getMessage());
	}

	/** 422 */
	@ExceptionHandler(TurnstileFailedException.class)
	ProblemDetail handleTurnstileFailed(TurnstileFailedException ex) {
		return problem(HttpStatus.UNPROCESSABLE_CONTENT, problemTypes.turnstileFailed(),
				"Turnstile verification failed", ex.getMessage());
	}

	/** 429 — the window is known, so the client is told exactly how long to wait. */
	@ExceptionHandler(RateLimitedException.class)
	ResponseEntity<ProblemDetail> handleRateLimited(RateLimitedException ex) {
		ProblemDetail body = problem(HttpStatus.TOO_MANY_REQUESTS, problemTypes.rateLimited(),
				"Too many requests", ex.getMessage());
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.header(HttpHeaders.RETRY_AFTER, Long.toString(ex.getRetryAfterSeconds()))
				.body(body);
	}

	/** 503 */
	@ExceptionHandler(UpstreamUnavailableException.class)
	ResponseEntity<ProblemDetail> handleUpstreamUnavailable(UpstreamUnavailableException ex) {
		log.warn("Upstream dependency unavailable: {}", ex.getMessage(), ex);
		ProblemDetail body = problem(HttpStatus.SERVICE_UNAVAILABLE, problemTypes.upstreamUnavailable(),
				"Service unavailable", ex.getMessage());
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
				.header(HttpHeaders.RETRY_AFTER, Long.toString(ex.getRetryAfterSeconds()))
				.body(body);
	}

	/** 500 — nothing about the cause is exposed; the correlation id ties the response to the log. */
	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception ex) {
		String correlationId = UUID.randomUUID().toString();
		log.error("Unhandled exception [correlationId={}]", correlationId, ex);
		ProblemDetail body = problem(HttpStatus.INTERNAL_SERVER_ERROR, problemTypes.internal(),
				"Internal server error", "An unexpected error occurred.");
		body.setProperty("correlationId", correlationId);
		return body;
	}

	private static ProblemDetail problem(HttpStatus status, URI type, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatus(status);
		problem.setType(type);
		problem.setTitle(title);
		if (detail != null && !detail.isBlank()) {
			problem.setDetail(detail);
		}
		return problem;
	}

	/**
	 * Field-level errors as plain maps so the JSON is exactly {@code [{"field":…,"message":…}]};
	 * the contract defines no other properties here.
	 */
	private static List<Map<String, String>> fieldErrors(BindingResult bindingResult) {
		List<Map<String, String>> errors = new ArrayList<>();
		for (ObjectError objectError : bindingResult.getAllErrors()) {
			String field = (objectError instanceof FieldError fieldError)
					? fieldError.getField()
					: objectError.getObjectName();
			String message = (objectError.getDefaultMessage() != null)
					? objectError.getDefaultMessage()
					: "is invalid";
			errors.add(error(field, message));
		}
		return errors;
	}

	private static Map<String, String> error(String field, String message) {
		Map<String, String> error = new LinkedHashMap<>(2);
		error.put("field", field);
		error.put("message", message);
		return error;
	}

	/** Our {@code type} for the statuses the contract describes, {@code null} for anything else. */
	private URI typeFor(HttpStatusCode status) {
		return switch (status.value()) {
			case 400 -> problemTypes.validation();
			case 401 -> problemTypes.unauthorized();
			case 404 -> problemTypes.notFound();
			case 429 -> problemTypes.rateLimited();
			case 503 -> problemTypes.upstreamUnavailable();
			case 500 -> problemTypes.internal();
			default -> null;
		};
	}

	/** Titles stay identical to the ones our own exceptions produce for the same status. */
	private static String titleFor(HttpStatusCode status) {
		return switch (status.value()) {
			case 400 -> "Validation failed";
			case 401 -> "Unauthorized";
			case 404 -> "Not found";
			case 429 -> "Too many requests";
			case 503 -> "Service unavailable";
			case 500 -> "Internal server error";
			default -> {
				HttpStatus resolved = HttpStatus.resolve(status.value());
				yield (resolved != null) ? resolved.getReasonPhrase() : "Request failed";
			}
		};
	}

}
