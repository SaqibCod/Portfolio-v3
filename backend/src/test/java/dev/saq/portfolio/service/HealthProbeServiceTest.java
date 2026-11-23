package dev.saq.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import dev.saq.portfolio.config.StatusProperties;
import dev.saq.portfolio.model.ServiceState;
import dev.saq.portfolio.model.StatusCheck;
import dev.saq.portfolio.repository.StatusCheckRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * State classification, which is the whole of what a probe decides.
 *
 * <p>The round trip is driven by a fake nanosecond source rather than by a real slow server, so
 * the degraded threshold can be tested at the millisecond either side of the line without any
 * test sleeping. The timeout case uses a stubbed {@link SocketTimeoutException}, which is what
 * the JDK client actually raises and what the {@code RestClient} wraps.
 */
class HealthProbeServiceTest {

	private static final String PROBE_URL = "https://githmind.example.com/actuator/health";

	private static final Instant CHECKED_AT = Instant.parse("2026-11-22T10:35:00Z");

	private static final Duration DEGRADED_AT = Duration.ofSeconds(2);

	private static final StatusProperties.Service GITHMIND = new StatusProperties.Service(
			"githmind", "githMind", "https://githmind.example.com", "/actuator/health");

	private MockRestServiceServer server;

	private StatusCheckRepository repository;

	private RestClient restClient;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		this.server = MockRestServiceServer.bindTo(builder).build();
		this.restClient = builder.build();
		this.repository = mock(StatusCheckRepository.class);
		// save() returns its argument, so assertions can read the check that was stored.
		when(repository.save(any(StatusCheck.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void aFastSuccessfulProbeIsOperational() {
		server.expect(requestTo(PROBE_URL)).andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess());

		StatusCheck check = probeWithRoundTrip(Duration.ofMillis(182));

		assertThat(check.state()).isEqualTo(ServiceState.OPERATIONAL);
		assertThat(check.responseTimeMs()).isEqualTo(182);
		assertThat(check.checkedAt()).isEqualTo(CHECKED_AT);
		server.verify();
	}

	@Test
	void aSlowSuccessfulProbeIsDegraded() {
		server.expect(requestTo(PROBE_URL)).andRespond(withSuccess());

		StatusCheck check = probeWithRoundTrip(Duration.ofMillis(2400));

		assertThat(check.state()).isEqualTo(ServiceState.DEGRADED);
		// Still measured: degraded means slow, not unreachable, and the number is the point.
		assertThat(check.responseTimeMs()).isEqualTo(2400);
	}

	@Test
	void theThresholdItselfCountsAsDegraded() {
		// The boundary is documented as "at or beyond", so exactly 2000 ms is degraded and one
		// millisecond under it is not.
		server.expect(requestTo(PROBE_URL)).andRespond(withSuccess());
		assertThat(probeWithRoundTrip(DEGRADED_AT).state()).isEqualTo(ServiceState.DEGRADED);

		setUp();
		server.expect(requestTo(PROBE_URL)).andRespond(withSuccess());
		assertThat(probeWithRoundTrip(DEGRADED_AT.minusMillis(1)).state())
				.isEqualTo(ServiceState.OPERATIONAL);
	}

	@Test
	void aServerErrorIsDown() {
		server.expect(requestTo(PROBE_URL)).andRespond(withServerError());

		StatusCheck check = probeWithRoundTrip(Duration.ofMillis(50));

		assertThat(check.state()).isEqualTo(ServiceState.DOWN);
		assertThat(check.responseTimeMs())
				.as("time spent failing is not a response time")
				.isNull();
	}

	@Test
	void aNotFoundHealthPathIsDown() {
		server.expect(requestTo(PROBE_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThat(probeWithRoundTrip(Duration.ofMillis(50)).state())
				.isEqualTo(ServiceState.DOWN);
	}

	@Test
	void aRedirectIsDown() {
		// The probe client does not follow redirects: a health endpoint that answers 302 has not
		// reported itself healthy, and following it would measure whatever sits behind it.
		server.expect(requestTo(PROBE_URL)).andRespond(withStatus(HttpStatus.FOUND));

		assertThat(probeWithRoundTrip(Duration.ofMillis(50)).state())
				.isEqualTo(ServiceState.DOWN);
	}

	@Test
	void aTimeoutIsDown() {
		server.expect(requestTo(PROBE_URL))
				.andRespond(withException(new SocketTimeoutException("Read timed out")));

		StatusCheck check = probeWithRoundTrip(Duration.ofSeconds(10));

		assertThat(check.state()).isEqualTo(ServiceState.DOWN);
		assertThat(check.responseTimeMs()).isNull();
	}

	@Test
	void aRefusedConnectionIsDown() {
		server.expect(requestTo(PROBE_URL))
				.andRespond(withException(new IOException("Connection refused")));

		assertThat(probeWithRoundTrip(Duration.ofMillis(5)).state())
				.isEqualTo(ServiceState.DOWN);
	}

	@Test
	void recordsTheCheckAgainstTheConfiguredSlug() {
		server.expect(requestTo(PROBE_URL)).andRespond(withSuccess());

		assertThat(probeWithRoundTrip(Duration.ofMillis(10)).serviceSlug()).isEqualTo("githmind");
	}

	@Test
	void stampsTheCheckFromTheClockWhenNoTimestampIsGiven() {
		server.expect(requestTo(PROBE_URL)).andRespond(withSuccess());

		HealthProbeService service = probeService(Duration.ofMillis(10));

		assertThat(service.probe(GITHMIND).checkedAt()).isEqualTo(CHECKED_AT);
		assertThat(service.now()).isEqualTo(CHECKED_AT);
	}

	/** Probes once, with the round trip the nanosecond source will report. */
	private StatusCheck probeWithRoundTrip(Duration roundTrip) {
		return probeService(roundTrip).probe(GITHMIND, CHECKED_AT);
	}

	private HealthProbeService probeService(Duration roundTrip) {
		return new HealthProbeService(restClient, properties(), repository,
				Clock.fixed(CHECKED_AT, ZoneOffset.UTC), elapsing(roundTrip));
	}

	/**
	 * A nanosecond source that jumps by {@code roundTrip} between its first and second reading,
	 * which is exactly how the service measures a probe.
	 */
	private static LongSupplier elapsing(Duration roundTrip) {
		long[] readings = { 0L, TimeUnit.MILLISECONDS.toNanos(roundTrip.toMillis()) };
		int[] next = { 0 };
		return () -> readings[Math.min(next[0]++, readings.length - 1)];
	}

	private static StatusProperties properties() {
		return new StatusProperties(Duration.ofMinutes(5), Duration.ofSeconds(10), DEGRADED_AT,
				Duration.ofDays(35), List.of(GITHMIND));
	}

}
