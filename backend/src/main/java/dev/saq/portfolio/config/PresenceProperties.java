package dev.saq.portfolio.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Limits for the realtime presence endpoint, bound from {@code app.presence}.
 *
 * <p>All three are ceilings rather than features: presence is a decoration on a portfolio, and
 * none of these numbers should ever be reached by ordinary traffic. They exist so that a script
 * pointed at {@code /ws/presence} costs a bounded amount of memory and bandwidth instead of an
 * unbounded one.
 *
 * <p>Every value carries a default, so a deployment may configure nothing here at all.
 *
 * @param maxSessions         how many connections are held at once. The 501st handshake is
 *                            accepted and then closed, because a WebSocket cannot be refused
 *                            after the upgrade without a close frame
 * @param staleAfter          how long a connection may go without a {@code ping} before the
 *                            sweeper closes it. Must stay comfortably above the client's 25s
 *                            heartbeat, or healthy visitors are disconnected between beats
 * @param cursorRatePerSecond how many {@code cursor} messages one connection may send each
 *                            second; the rest are dropped. Matches the throttle the contract
 *                            documents for clients, so a well-behaved one never hits it
 */
@Validated
@ConfigurationProperties(prefix = "app.presence")
public record PresenceProperties(
		@Min(1) @DefaultValue("500") int maxSessions,
		@NotNull @DefaultValue("60s") Duration staleAfter,
		@Min(1) @DefaultValue("20") int cursorRatePerSecond) {
}
