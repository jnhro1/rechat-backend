package com.jnhro1.rechatbackend.participant.response;

import com.jnhro1.rechatbackend.participant.SessionParticipant;
import com.jnhro1.rechatbackend.participant.enums.PresenceStatus;
import java.time.Instant;

public record SessionParticipantResponse(
        Long id,
        Long sessionId,
        String userId,
        PresenceStatus presence,
        Instant joinedAt,
        Instant leftAt,
        Instant lastSeenAt
) {

    public static SessionParticipantResponse from(SessionParticipant participant) {
        return new SessionParticipantResponse(
                participant.getId(),
                participant.getSessionId(),
                participant.getUserId(),
                participant.getPresence(),
                participant.getJoinedAt(),
                participant.getLeftAt(),
                participant.getLastSeenAt()
        );
    }
}
