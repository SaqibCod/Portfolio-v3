package dev.saq.portfolio.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Which deployed projects the status page watches, and how they are probed.
 *
 * <p>The service list is the whole feature switch. An empty list is a supported, fully working
 * configuration -- the probe job has nothing to visit, {@code /api/v1/status} answers with an
 * empty list, and the site hides its status page -- so bringing a project online is a matter of
 * adding four lines to {@code application.yml} and restarting. Nothing in the code has to change.
 *
 * <p>Every timing value carries a default, so a deployment that is happy with the defaults may
 * configure nothing but {@code services}.
 *
 * @param interval          how often every service is probed
 * @param timeout           how long one probe may take before it counts as down
 * @param degradedThreshold a successful probe at or above this round trip is degraded, not
 *                          operational
 * @param retention         how long probe results are kept; the retention job deletes past this
 * @param services          the projects to probe, possibly none
 */
@Validated
@ConfigurationProperties(prefix = "app.status")
public record StatusProperties(
		@NotNull @DefaultValue("5m") Duration interval,
		@NotNull @DefaultValue("10s") Duration timeout,
		@NotNull @DefaultValue("2s") Duration degradedThreshold,
		@NotNull @DefaultValue("35d") Duration retention,
		List<@Valid Service> services) {

	/**
	 * Normalises the list so that "no services configured" and "an empty services list" are the
	 * same thing everywhere downstream, and so no caller can mutate the configuration.
	 */
	public StatusProperties {
		services = services == null ? List.of() : List.copyOf(services);
	}

	/** True when nothing is being monitored, which is a valid state rather than a misconfiguration. */
	public boolean hasNoServices() {
		return services.isEmpty();
	}

	/** The configured service with this slug, or {@code null} if nothing by that name is monitored. */
	public Service findBySlug(String slug) {
		return services.stream()
				.filter(service -> service.slug().equals(slug))
				.findFirst()
				.orElse(null);
	}

	/**
	 * One monitored project.
	 *
	 * @param slug       the identifier the API and the site use, e.g. {@code githmind}. Matches the
	 *                   {@code serviceSlug} pattern in the contract, and is what
	 *                   {@code status_check.service_slug} stores
	 * @param name       the display name, e.g. {@code githMind}
	 * @param url        the project's public URL, shown on the status page and used as the base of
	 *                   the probe
	 * @param healthPath the path appended to {@code url} when probing. A dedicated health endpoint
	 *                   rather than the home page, so a probe measures the application rather than
	 *                   a CDN edge
	 */
	public record Service(
			@NotBlank @Pattern(regexp = "^[a-z0-9-]+$",
					message = "must be lowercase letters, digits and hyphens") String slug,
			@NotBlank String name,
			@NotBlank String url,
			@DefaultValue("/actuator/health") String healthPath) {

		/** The absolute URL one probe requests, with exactly one slash between the two halves. */
		public String probeUrl() {
			String base = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
			String path = healthPath.startsWith("/") ? healthPath : "/" + healthPath;
			return base + path;
		}

	}

}
