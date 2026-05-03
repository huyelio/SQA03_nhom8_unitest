package com.example.AuthService;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test ngữ cảnh Spring. Cần MySQL theo {@code application.properties}; tắt mặc định để {@code mvn test}
 * (unit test service mock) chạy offline. Bật lại khi có DB hoặc profile test + H2.
 */
@SpringBootTest
@Disabled("Requires live MySQL from application.properties; unit tests cover services with mocks.")
class AuthServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
