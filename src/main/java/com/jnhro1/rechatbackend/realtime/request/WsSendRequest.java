package com.jnhro1.rechatbackend.realtime.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record WsSendRequest(
        String eventId,
        @NotBlank(message = "content는 필수입니다.") String content,
        @NotNull(message = "occurredAt은 필수입니다.") Instant occurredAt
) {
}
