package com.jnhro1.rechatbackend.restore.response;

import java.time.Instant;
import java.util.List;

public record TimelineResponse(
        Instant at,
        long upToSequence,
        List<TimelineParticipant> participants,
        List<TimelineMessage> messages
) {
}
