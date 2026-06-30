package com.jnhro1.rechatbackend.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

public record ApiResponse<T>(
        @JsonInclude(JsonInclude.Include.NON_NULL) T data,
        @JsonInclude(JsonInclude.Include.NON_NULL) ErrorResponse error,
        Instant timestamp
) {

    public static <T> ApiResponse<T> success(T data) {
        // FYI: 응답 메타데이터 timestamp는 결정성이 불필요한 영역이라 Instant.now() 직접 사용.
        return new ApiResponse<>(data, null, Instant.now());
    }

    public static ApiResponse<Void> failure(ErrorResponse error) {
        return new ApiResponse<>(null, error, Instant.now());
    }
}
