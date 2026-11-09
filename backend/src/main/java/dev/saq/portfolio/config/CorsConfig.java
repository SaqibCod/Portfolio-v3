



package dev.saq.portfolio.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS for the browser frontend. Origins are configuration, never wildcards: the API is called
 * from the portfolio site only.
 */
@Configuration(proxyBeanMethods = false)
public class CorsConfig implements WebMvcConfigurer {

	private final AppProperties properties;

	public CorsConfig(AppProperties properties) {
		this.properties = properties;
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**")
				.allowedOrigins(properties.cors().allowedOrigins().toArray(String[]::new))
				.allowedMethods("GET", "POST", "PATCH", "DELETE")
				.allowedHeaders("Authorization", "Content-Type")
				.exposedHeaders("RateLimit-Limit", "RateLimit-Remaining", "Retry-After")
				// No cookies or sessions are used, so credentials are never needed.
				.allowCredentials(false)
				.maxAge(3600);
	}

}
