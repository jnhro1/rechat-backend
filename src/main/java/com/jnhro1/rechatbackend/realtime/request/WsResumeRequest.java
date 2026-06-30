package com.jnhro1.rechatbackend.realtime.request;

import jakarta.validation.constraints.Min;

public record WsResumeRequest(
        @Min(value = 0, message = "afterSequence는 0 이상이어야 합니다.") long afterSequence,
        Integer limit
) {
    public int limitOrDefault() {
        return (limit == null || limit <= 0) ? 50 : Math.min(limit, 200);
    }
}
