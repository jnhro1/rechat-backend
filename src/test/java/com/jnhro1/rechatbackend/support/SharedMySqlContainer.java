package com.jnhro1.rechatbackend.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.MySQLContainer;

/**
 * JVM당 1개만 기동되는 공유 MySQL 컨테이너. MOCK 기반/포트 기반 통합 테스트 베이스가 함께 재사용한다.
 */
public final class SharedMySqlContainer {

    public static final MySQLContainer<?> INSTANCE = new MySQLContainer<>("mysql:8.0");

    static {
        INSTANCE.start();
    }

    private SharedMySqlContainer() {
    }

    public static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", INSTANCE::getJdbcUrl);
        registry.add("spring.datasource.username", INSTANCE::getUsername);
        registry.add("spring.datasource.password", INSTANCE::getPassword);
    }
}
