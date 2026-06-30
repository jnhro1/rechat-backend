package com.jnhro1.rechatbackend.support;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 통합 테스트 공통 베이스 (CONVENTIONS §13.2). MOCK 환경 + 공유 Testcontainers MySQL.
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        SharedMySqlContainer.registerProperties(registry);
    }
}
