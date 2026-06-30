package com.jnhro1.rechatbackend.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class IdempotencyKeyResolverTest {

    @Test
    @DisplayName("resolve - 제공된 키는 그대로 반환한다")
    void resolve_returnsProvidedKey() {
        String resolved = IdempotencyKeyResolver.resolve("client-key-123");

        assertThat(resolved).isEqualTo("client-key-123");
    }

    @Test
    @DisplayName("resolve - 앞뒤 공백은 trim 후 반환한다")
    void resolve_trimsProvidedKey() {
        String resolved = IdempotencyKeyResolver.resolve("  key-with-spaces  ");

        assertThat(resolved).isEqualTo("key-with-spaces");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("resolve - 키가 null/빈값/공백이면 non-null UUID를 생성한다")
    void resolve_generatesUuidWhenBlank(String blankKey) {
        String resolved = IdempotencyKeyResolver.resolve(blankKey);

        assertThat(resolved).isNotBlank();
        // UUID 형식(36자, 하이픈 4개) 확인
        assertThat(resolved).hasSize(36).matches("[0-9a-f-]{36}");
    }
}
