package com.jnhro1.rechatbackend.event.request;

import com.jnhro1.rechatbackend.event.enums.EventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record CollectEventRequest(
        @NotBlank(message = "eventId는 필수입니다.") String eventId,
        @NotNull(message = "type은 필수입니다.") EventType type,
        @NotBlank(message = "senderId는 필수입니다.") String senderId,
        @NotBlank(message = "content는 필수입니다.") String content,
        @NotNull(message = "occurredAt은 필수입니다.") Instant occurredAt
) {
}
