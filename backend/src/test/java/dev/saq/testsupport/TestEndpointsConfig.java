package dev.saq.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Registers {@link TestEndpointsController}; import it from any test that needs the endpoints,
 * alongside {@link WebInfrastructureTestConfig}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestEndpointsConfig {

	@Bean
	TestEndpointsController testEndpointsController() {
		return new TestEndpointsController();
	}

}
