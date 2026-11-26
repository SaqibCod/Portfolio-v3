package dev.saq.portfolio.config;

import dev.saq.portfolio.websocket.PresenceWebSocketHandler;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers the one WebSocket endpoint this API has.
 *
 * <p>{@code /ws/presence} sits outside {@code /api/v1} on purpose: it is not a versioned
 * resource, it carries its own message format (documented in the contract's description, since
 * OpenAPI cannot express it), and it is not reached through {@link CorsConfig}, whose mapping
 * stops at {@code /api/**}. A WebSocket handshake is not a CORS preflight -- browsers send an
 * {@code Origin} header and expect the server to check it itself -- which is what the allowed
 * origins below do.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

	private final PresenceWebSocketHandler presenceWebSocketHandler;

	private final AppProperties appProperties;

	public WebSocketConfig(PresenceWebSocketHandler presenceWebSocketHandler,
			AppProperties appProperties) {

		this.presenceWebSocketHandler = presenceWebSocketHandler;
		this.appProperties = appProperties;
	}

	/**
	 * Allowed origins come from the same {@code app.cors.allowed-origins} the REST API uses, so
	 * there is one list to keep right rather than two that can disagree.
	 *
	 * <p>Spring always installs an {@code OriginHandshakeInterceptor}, and with an empty list it
	 * accepts same-origin requests only -- which for an API on its own subdomain means it would
	 * accept nothing from the site at all. So this call is load-bearing, not a hardening extra.
	 * A rejected handshake is answered with 403 before any session exists.
	 *
	 * <p>This is a browser protection and nothing more: {@code Origin} is a header, and any
	 * non-browser client can send whatever it likes. The real limits on abuse are the frame size
	 * in {@link dev.saq.portfolio.websocket.PresenceWebSocketHandler}, the session ceiling and
	 * the cursor budget in {@link PresenceProperties} -- none of which trust the origin.
	 */
	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(this.presenceWebSocketHandler, "/ws/presence")
				.setAllowedOrigins(this.appProperties.cors().allowedOrigins().toArray(String[]::new));
	}

}
