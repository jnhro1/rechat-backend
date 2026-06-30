package com.jnhro1.rechatbackend.restore;

import com.jnhro1.rechatbackend.participant.enums.PresenceStatus;
import com.jnhro1.rechatbackend.restore.response.TimelineMessage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;

@Getter
public class SessionState {

    private final Map<String, Participant> participants = new LinkedHashMap<>();
    private final List<TimelineMessage> messages = new ArrayList<>();
    private long upToSequence;

    public void addMessage(long serverSequence, String senderId, String content, Instant occurredAt) {
        messages.add(new TimelineMessage(serverSequence, senderId, content, occurredAt));
    }

    public void join(String userId, Instant occurredAt) {
        Participant participant = participants.computeIfAbsent(userId, id -> new Participant());
        participant.presence = PresenceStatus.ONLINE;
        participant.joinedAt = occurredAt;
        participant.left = false;
    }

    public void transition(String userId, PresenceStatus presence, boolean left) {
        Participant participant = participants.get(userId);
        if (participant != null) {
            participant.presence = presence;
            participant.left = left;
        }
    }

    public void advanceTo(long serverSequence) {
        this.upToSequence = serverSequence;
    }

    @Getter
    public static class Participant {
        private PresenceStatus presence;
        private Instant joinedAt;
        private boolean left;
    }
}
