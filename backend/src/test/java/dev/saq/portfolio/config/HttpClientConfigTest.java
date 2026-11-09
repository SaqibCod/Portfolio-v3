package dev.saq.portfolio.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Proves the configured timeouts are actually applied to outbound calls, using a real local
 * server so the JDK HTTP client is exercised end to end.
 */
class HttpClientConfigTest {

	private HttpServer server;

	@BeforeEach
	void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/fast", exchange -> respond(exchange, "ok"));
		server.createContext("/slow", exchange -> {
			try {
				TimeUnit.SECONDS.sleep(5);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			respond(exchange, "late");
		});
		server.start();
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	@Test
	void usesTheTimeoutsTheContractExpects() {
		assertThat(HttpClientConfig.CONNECT_TIMEOUT).isEqualTo(Duration.ofSeconds(3));
		assertThat(HttpClientConfig.READ_TIMEOUT).isEqualTo(Duration.ofSeconds(10));
	}

	@Test
	void buildsAWorkingClient() {
		RestClient client = HttpClientConfig.builder(Duration.ofSeconds(1), Duration.ofSeconds(2))
				.baseUrl(baseUrl())
				.build();

		assertThat(client.get().uri("/fast").retrieve().body(String.class)).isEqualTo("ok");
	}

	@Test
	void abortsAResponseThatExceedsTheReadTimeout() {
		RestClient client = HttpClientConfig.builder(Duration.ofSeconds(1), Duration.ofMillis(250))
				.baseUrl(baseUrl())
				.build();

		assertThatThrownBy(() -> client.get().uri("/slow").retrieve().body(String.class))
				.isInstanceOf(ResourceAccessException.class);
	}

	@Test
	void autoConfiguredBuilderIsOurs() {
		assertThat(new HttpClientConfig().restClientBuilder()).isNotNull();
	}

	private String baseUrl() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.sendResponseHeaders(200, bytes.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(bytes);
		}
	}

}
