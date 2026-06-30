package com.jnhro1.rechatbackend.restore.snapshot;

import com.jnhro1.rechatbackend.restore.SessionState;
import com.jnhro1.rechatbackend.participant.enums.PresenceStatus;
import com.jnhro1.rechatbackend.restore.response.TimelineMessage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public record SnapshotState(
        long upToSequence,
        List<SnapshotParticipant> participants,
        List<SnapshotMessage> messages
) {

    public static final int SNAPSHOT_MESSAGE_LIMIT = 200;

    public record SnapshotParticipant(String userId, PresenceStatus presence, Instant joinedAt, boolean left) {
    }

    public record SnapshotMessage(long serverSequence, String senderId, String content, Instant occurredAt) {
    }

    public static SnapshotState from(SessionState state) {
        List<SnapshotParticipant> participants = new ArrayList<>();
        state.getParticipants().forEach((userId, p) ->
                participants.add(new SnapshotParticipant(userId, p.getPresence(), p.getJoinedAt(), p.isLeft())));

        List<TimelineMessage> all = state.getMessages();
        List<TimelineMessage> recent = all.size() <= SNAPSHOT_MESSAGE_LIMIT
                ? all : all.subList(all.size() - SNAPSHOT_MESSAGE_LIMIT, all.size());
        List<SnapshotMessage> messages = new ArrayList<>();
        recent.forEach(m -> messages.add(new SnapshotMessage(m.serverSequence(), m.senderId(), m.content(), m.occurredAt())));

        return new SnapshotState(state.getUpToSequence(), participants, messages);
    }

    public SessionState toSessionState() {
        SessionState state = new SessionState();
        participants.forEach(p -> {
            state.join(p.userId(), p.joinedAt());
            state.transition(p.userId(), p.presence(), p.left());
        });
        messages.forEach(m -> state.addMessage(m.serverSequence(), m.senderId(), m.content(), m.occurredAt()));
        state.advanceTo(upToSequence);
        return state;
    }
}
