package dev.saq.testsupport;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * An endpoint under {@code /api/v1/admin/**} so the admin token filter can be tested without a
 * real admin feature. Registered explicitly by {@link TestEndpointsConfig}.
 */
@RestController
@RequestMapping("/api/v1/admin/test")
public class TestAdminController {

	@GetMapping("/ping")
	String ping() {
		return "admin-pong";
	}

}
