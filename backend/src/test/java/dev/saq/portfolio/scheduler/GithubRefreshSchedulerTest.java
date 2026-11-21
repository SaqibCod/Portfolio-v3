package dev.saq.portfolio.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.time.Instant;
import java.util.List;

import dev.saq.portfolio.dto.response.GithubActivityResponse;
import dev.saq.portfolio.dto.response.GithubSummaryResponse;
import dev.saq.portfolio.exception.UpstreamUnavailableException;
import dev.saq.portfolio.service.GithubService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/** The refresh job: evict, then re-read, and never let a failure escape. */
@ExtendWith(MockitoExtension.class)
class GithubRefreshSchedulerTest {

	@Mock
	private GithubService githubService;

	@Test
	void evictsBeforeReadingSoTheReadActuallyFetches() {
		given(githubService.getSummary()).willReturn(summary());

		new GithubRefreshScheduler(githubService).refreshSummary();

		// Order is the whole mechanism: reading first would be answered from the warm cache and
		// fetch nothing, and evicting without reading would leave the next visitor paying for it.
		InOrder inOrder = Mockito.inOrder(githubService);
		inOrder.verify(githubService).evictSummary();
		inOrder.verify(githubService).getSummary();
	}

	@Test
	void primesTheSizeAVisitorsRequestWillAskFor() {
		given(githubService.getActivity(anyInt())).willReturn(activity());

		new GithubRefreshScheduler(githubService).refreshActivity();

		// The cache is keyed by limit, so priming the maximum instead would warm an entry
		// nothing ever reads and leave the default to be fetched by the next visitor.
		InOrder inOrder = Mockito.inOrder(githubService);
		inOrder.verify(githubService).evictActivity();
		inOrder.verify(githubService).getActivity(GithubService.DEFAULT_ACTIVITY_ITEMS);
	}

	@Test
	void readsActivityExactlyOnce() {
		// One read is enough for both jobs: it warms the default entry, and the fetch behind it
		// maps the full twenty items into the last good value whatever size was asked for.
		given(githubService.getActivity(anyInt())).willReturn(activity());

		new GithubRefreshScheduler(githubService).refreshActivity();

		then(githubService).should(times(1)).getActivity(anyInt());
	}

	@Test
	void swallowsAFailedSummaryRefresh() {
		// The service has already logged it with its own context, and the next tick retries.
		willThrow(new UpstreamUnavailableException("GitHub is unavailable."))
				.given(githubService).getSummary();

		assertThatCode(() -> new GithubRefreshScheduler(githubService).refreshSummary())
				.doesNotThrowAnyException();
	}

	@Test
	void swallowsAFailedActivityRefresh() {
		willThrow(new UpstreamUnavailableException("GitHub is unavailable."))
				.given(githubService).getActivity(anyInt());

		assertThatCode(() -> new GithubRefreshScheduler(githubService).refreshActivity())
				.doesNotThrowAnyException();
	}

	@Test
	void keepsTheTwoRefreshesSeparate() {
		given(githubService.getSummary()).willReturn(summary());

		new GithubRefreshScheduler(githubService).refreshSummary();

		then(githubService).should(never()).evictActivity();
		then(githubService).should(never()).getActivity(anyInt());
	}

	private static GithubSummaryResponse summary() {
		return new GithubSummaryResponse("test-user", 0, List.of(), List.of(), Instant.now());
	}

	private static GithubActivityResponse activity() {
		return new GithubActivityResponse(List.of(), Instant.now());
	}

}
