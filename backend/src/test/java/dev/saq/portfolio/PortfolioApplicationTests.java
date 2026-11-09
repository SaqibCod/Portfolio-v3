package dev.saq.portfolio;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@SpringBootTest
class PortfolioApplicationTests {

	@Test
	void contextLoads() {
	}

}
