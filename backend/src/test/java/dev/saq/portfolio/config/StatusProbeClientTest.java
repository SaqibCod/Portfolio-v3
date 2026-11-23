package dev.saq.portfolio.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.saq.portfolio.model.ServiceState;
import dev.saq.portfolio.model.StatusCheck;
import dev.saq.portfolio.repository.StatusCheckRepository;
import dev.saq.portfolio.service.HealthProbeService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * A regression test for the one way a probe can lie.
 *
 * <p>The JDK's HTTP client defaults to HTTP/2, and over cleartext {@code http://} that makes
 * every request an HTTP/1.1 request carrying an {@code Upgrade: h2c} offer. A server may ignore
 * the offer, but not all of them do: some close the connection instead, and the client then
 * fails with "header parser received no bytes". A probe reads that as DOWN — so a perfectly
 * healthy service is published as an outage, which is the single worst thing a status page can
 * get wrong. It happened against the Next.js dev server on the first real run.
 *
 * <p>The server here is a raw socket rather than a mock or {@code MockRestServiceServer}:
 * nothing above the wire can reproduce a connection that dies on a request header. It answers an
 * ordinary HTTP/1.1 request with {@code 200} and hangs up on anything offering h2c, so the two
 * tests below cannot both pass by accident.
 *
 * <p>Lives in this package so it can exercise the real {@code statusProbeRestClient} bean rather
 * than a lookalike built in the test.
 */
class StatusProbeClientTest {

	private static final Duration TIMEOUT = Duration.ofSeconds(2);

	private UpgradeHostileServer server;

	private StatusCheckRepository repository;

	@BeforeEach
	void startServer() throws IOException {
		server = new UpgradeHostileServer();
		server.start();
		repository = mock(StatusCheckRepository.class);
		when(repository.save(any(StatusCheck.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
	}

	@AfterEach
	void stopServer() throws IOException {
		server.close();
	}

	@Test
	void theProbeClientReachesAServerThatHangsUpOnAnUpgradeOffer() {
		RestClient probeClient =
				new HttpClientConfig().statusProbeRestClient(properties());

		StatusCheck check = probe(probeClient);

		assertThat(check.state()).isEqualTo(ServiceState.OPERATIONAL);
		assertThat(server.sawUpgradeOffer())
				.as("the probe client must not offer h2c")
				.isFalse();
	}

	@Test
	void theHttp2DefaultIsWhatWouldHaveReportedAHealthyServiceAsDown() {
		// Pins why the probe client is configured as it is. Should a future JDK stop offering
		// h2c on cleartext requests, this starts failing — which is the moment to revisit
		// HttpClientConfig rather than to relax the assertion.
		RestClient defaultClient =
				HttpClientConfig.builder(TIMEOUT, TIMEOUT, HttpClient.Version.HTTP_2).build();

		StatusCheck check = probe(defaultClient);

		assertThat(check.state()).isEqualTo(ServiceState.DOWN);
		assertThat(server.sawUpgradeOffer()).isTrue();
	}

	@Test
	void theProbeClientUsesTheConfiguredTimeoutRatherThanTheSharedOne() {
		// The read timeout is the line between "slow" and "down", and it is the owner's setting.
		assertThat(properties().timeout()).isEqualTo(TIMEOUT);
		assertThat(HttpClientConfig.READ_TIMEOUT).isNotEqualTo(TIMEOUT);
	}

	private StatusCheck probe(RestClient client) {
		return new HealthProbeService(client, properties(), repository)
				.probe(service(), Instant.parse("2026-11-22T10:35:00Z"));
	}

	private StatusProperties properties() {
		return new StatusProperties(Duration.ofMinutes(5), TIMEOUT, Duration.ofSeconds(2),
				Duration.ofDays(35), List.of(service()));
	}

	private StatusProperties.Service service() {
		return new StatusProperties.Service("stub", "Stub",
				"http://localhost:" + server.port(), "/");
	}

	/**
	 * An HTTP/1.1 server that closes the connection on any request offering an h2c upgrade.
	 *
	 * <p>Deliberately unforgiving, because that is the behaviour being guarded against. A server
	 * that merely ignored the header would let both tests above pass for the wrong reason.
	 */
	private static final class UpgradeHostileServer implements AutoCloseable {

		private final ServerSocket socket = new ServerSocket(0);

		private final List<String> requests = new CopyOnWriteArrayList<>();

		private final AtomicBoolean running = new AtomicBoolean(true);

		UpgradeHostileServer() throws IOException {
			socket.setSoTimeout((int) TIMEOUT.toMillis() * 4);
		}

		void start() {
			Thread.ofVirtual().name("upgrade-hostile-server").start(this::acceptLoop);
		}

		int port() {
			return socket.getLocalPort();
		}

		boolean sawUpgradeOffer() {
			return requests.stream()
					.anyMatch(request -> request.toLowerCase().contains("upgrade: h2c"));
		}

		private void acceptLoop() {
			while (running.get() && !socket.isClosed()) {
				try (Socket connection = socket.accept()) {
					handle(connection);
				}
				catch (IOException ex) {
					// Accept timed out, or the socket closed with the test. Either way there is
					// nothing left to serve.
					return;
				}
			}
		}

		private void handle(Socket connection) throws IOException {
			BufferedReader reader = new BufferedReader(new InputStreamReader(
					connection.getInputStream(), StandardCharsets.US_ASCII));

			StringBuilder request = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null && !line.isEmpty()) {
				request.append(line).append('\n');
			}
			String received = request.toString();
			if (received.isEmpty()) {
				return;
			}
			requests.add(received);

			if (received.toLowerCase().contains("upgrade: h2c")) {
				// The failure being reproduced: hang up without writing a single byte.
				return;
			}

			OutputStream out = connection.getOutputStream();
			out.write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok"
					.getBytes(StandardCharsets.US_ASCII));
			out.flush();
		}

		@Override
		public void close() throws IOException {
			running.set(false);
			socket.close();
		}

	}

}
