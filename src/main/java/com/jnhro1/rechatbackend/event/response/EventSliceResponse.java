package com.jnhro1.rechatbackend.event.response;

import java.util.List;

public record EventSliceResponse(
        List<SessionEventResponse> events,
        Long nextAfterSequence,
        boolean hasMore
) {

    public static EventSliceResponse of(List<SessionEventResponse> events, int limit) {
        Long nextAfter = events.isEmpty() ? null : events.get(events.size() - 1).serverSequence();
        boolean hasMore = events.size() == limit;
        return new EventSliceResponse(events, nextAfter, hasMore);
    }
}
