package com.jnhro1.rechatbackend.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    // FYI: 운영은 UTC 시스템 시계, 테스트는 Clock.fixed(...)로 교체.
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
