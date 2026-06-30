package com.jnhro1.rechatbackend.session.request;

import com.jnhro1.rechatbackend.session.enums.SessionStatus;
import java.time.Instant;

public record SessionSearchCondition(
        SessionStatus status,
        Instant from,
        Instant to,
        String participantId
) {

    public static SessionSearchCondition of(SessionStatus status, Instant from, Instant to, String participantId) {
        return new SessionSearchCondition(status, from, to, participantId);
    }
}
