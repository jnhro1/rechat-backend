package com.jnhro1.rechatbackend.restore.response;

import java.time.Instant;

public record TimelineMessage(
        long serverSequence,
        String senderId,
        String content,
        Instant occurredAt
) {
}
