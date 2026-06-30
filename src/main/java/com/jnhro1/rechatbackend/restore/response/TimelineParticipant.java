package com.jnhro1.rechatbackend.restore.response;

import com.jnhro1.rechatbackend.participant.enums.PresenceStatus;
import java.time.Instant;

public record TimelineParticipant(
        String userId,
        PresenceStatus presence,
        Instant joinedAt
) {
}
