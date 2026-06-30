package com.jnhro1.rechatbackend.session.response;

import com.jnhro1.rechatbackend.session.Session;
import com.jnhro1.rechatbackend.session.enums.SessionStatus;
import java.time.Instant;

public record SessionResponse(
        Long id,
        SessionStatus status,
        Instant startedAt,
        Instant endedAt,
        Instant createdAt
) {

    public static SessionResponse from(Session session) {
        return new SessionResponse(
                session.getId(),
                session.getStatus(),
                session.getStartedAt(),
                session.getEndedAt(),
                session.getCreatedAt()
        );
    }
}
