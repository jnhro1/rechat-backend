package com.jnhro1.rechatbackend.participant.request;

import jakarta.validation.constraints.NotBlank;

public record ParticipantRequest(
        @NotBlank(message = "userId는 필수입니다.") String userId,
        String eventId
) {
}
