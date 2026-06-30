package com.jnhro1.rechatbackend.event.response;

import java.util.List;

public record EventRangeResponse(
        List<SessionEventResponse> events,
        int count,
        boolean truncated
) {

    public static EventRangeResponse of(List<SessionEventResponse> events, int limit) {
        return new EventRangeResponse(events, events.size(), events.size() == limit);
    }
}
