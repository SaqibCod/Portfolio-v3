package dev.saq.portfolio.controller;

import java.time.Duration;

import dev.saq.portfolio.annotation.RateLimited;
import dev.saq.portfolio.dto.response.GithubActivityResponse;
import dev.saq.portfolio.dto.response.GithubSummaryResponse;
import dev.saq.portfolio.model.RateLimitPolicy;
import dev.saq.portfolio.service.GithubService;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** The two read-only GitHub endpoints behind the home page's "Currently building" section. */
@RestController
@RequestMapping("/api/v1/github")
public class GithubController {

	/**
	 * The contract's {@code limit} default, as a string because that is what
	 * {@code @RequestParam} takes.
	 *
	 * <p>Must stay equal to {@link GithubService#DEFAULT_ACTIVITY_ITEMS}, which is the size the
	 * refresh job keeps warm — if the two drifted apart, every visitor's first request after a
	 * refresh would miss the cache and go to GitHub. {@code GithubControllerTest} pins them.
	 */
	static final String DEFAULT_LIMIT = "5";

	/**
	 * Matches the summary cache's lifetime in spirit, not to the second.
	 *
	 * <p>These values tell browsers and any CDN in front of the API how long a response may be
	 * reused. An hour against a six-hour server-side cache is deliberately conservative: a
	 * visitor who reloads should pick up a refresh reasonably soon, and the request costs nothing
	 * upstream because the server cache absorbs it.
	 */
	private static final Duration SUMMARY_MAX_AGE = Duration.ofHours(1);

	/** One activity cache period, so a cached copy never outlives the data behind it. */
	private static final Duration ACTIVITY_MAX_AGE = Duration.ofMinutes(15);

	private final GithubService githubService;

	public GithubController(GithubService githubService) {
		this.githubService = githubService;
	}

	/**
	 * Contribution graph, top languages and totals.
	 *
	 * <p>A 503 from here means GitHub is unreachable and the server has never held an answer; any
	 * other failure is invisible, because the service keeps serving the last good value.
	 */
	@GetMapping("/summary")
	@RateLimited(RateLimitPolicy.PUBLIC_READ)
	public ResponseEntity<GithubSummaryResponse> summary() {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.maxAge(SUMMARY_MAX_AGE).cachePublic())
				.body(githubService.getSummary());
	}

	/**
	 * Recent commits, pull requests and releases.
	 *
	 * @param limit how many items to return, 1–20; out of range is a 400 rather than a clamp, to
	 *              match the guestbook's handling of the same kind of parameter
	 */
	@GetMapping("/activity")
	@RateLimited(RateLimitPolicy.PUBLIC_READ)
	public ResponseEntity<GithubActivityResponse> activity(
			@RequestParam(defaultValue = DEFAULT_LIMIT)
			@Min(value = 1, message = "must be at least 1")
			@Max(value = 20, message = "must be at most 20") int limit) {

		return ResponseEntity.ok()
				.cacheControl(CacheControl.maxAge(ACTIVITY_MAX_AGE).cachePublic())
				.body(githubService.getActivity(limit));
	}

}
