package com.jnhro1.rechatbackend.event;

import com.jnhro1.rechatbackend.event.response.SessionEventResponse;

public record CollectEventResult(boolean created, SessionEventResponse event) {
}
