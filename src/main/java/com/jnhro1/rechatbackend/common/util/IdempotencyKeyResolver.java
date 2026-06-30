package com.jnhro1.rechatbackend.common.util;

import java.util.UUID;

public final class IdempotencyKeyResolver {

    private IdempotencyKeyResolver() {
    }

    public static String resolve(String key) {
        // FYI: 제공된 키 경로는 결정적. 키 부재 시 UUID 생성 경로만 비결정적(키 생성기의 본질).
        if (key != null && !key.isBlank()) {
            return key.trim();
        }
        return UUID.randomUUID().toString();
    }
}
