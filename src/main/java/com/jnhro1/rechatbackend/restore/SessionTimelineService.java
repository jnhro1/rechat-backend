package com.jnhro1.rechatbackend.restore;
import com.jnhro1.rechatbackend.session.SessionRepository;
import com.jnhro1.rechatbackend.event.SessionEvent;
import com.jnhro1.rechatbackend.event.SessionEventRepository;
import com.jnhro1.rechatbackend.restore.snapshot.SessionSnapshot;
import com.jnhro1.rechatbackend.restore.snapshot.SessionSnapshotRepository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jnhro1.rechatbackend.session.exception.SessionNotFoundException;
import com.jnhro1.rechatbackend.restore.response.TimelineMessage;
import com.jnhro1.rechatbackend.restore.response.TimelineParticipant;
import com.jnhro1.rechatbackend.restore.response.TimelineResponse;
import com.jnhro1.rechatbackend.restore.snapshot.SnapshotState;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SessionTimelineService {

    private final SessionRepository sessionRepository;
    private final SessionEventRepository eventRepository;
    private final SessionSnapshotRepository snapshotRepository;
    private final SessionStateReducer reducer;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public TimelineResponse reconstruct(Long sessionId, Instant at, int messageLimit) {
        if (!sessionRepository.existsById(sessionId)) {
            throw new SessionNotFoundException(sessionId);
        }
        SessionState state = (at == null)
                ? reconstructCurrent(sessionId)
                : reconstructAt(sessionId, at);
        Instant resolvedAt = (at != null) ? at : Instant.now(clock);
        return toResponse(resolvedAt, state, messageLimit);
    }

    private SessionState reconstructCurrent(Long sessionId) {
        Optional<SessionSnapshot> snapshot = snapshotRepository.findTopBySessionIdOrderByUptoSequenceDesc(sessionId);
        SessionState base = snapshot.map(s -> deserialize(s.getState()).toSessionState()).orElse(null);
        long afterSequence = (base != null) ? base.getUpToSequence() : 0L;
        List<SessionEvent> remaining = eventRepository
                .findBySessionIdAndServerSequenceGreaterThanOrderByServerSequenceAsc(sessionId, afterSequence);
        return reducer.reduce(base, remaining);
    }

    /**
     * watermark(max_occurred_at)가 at 이하인 스냅샷만 base로 안전하게 재사용할 수 있다.
     * 그런 스냅샷이 없으면 occurredAt &le; at 이벤트를 처음부터 full replay(폴백).
     */
    private SessionState reconstructAt(Long sessionId, Instant at) {
        Optional<SessionSnapshot> base = snapshotRepository
                .findTopBySessionIdAndMaxOccurredAtLessThanEqualOrderByUptoSequenceDesc(sessionId, at);
        if (base.isEmpty()) {
            List<SessionEvent> events = eventRepository
                    .findBySessionIdAndOccurredAtLessThanEqualOrderByServerSequenceAsc(sessionId, at);
            return reducer.reduce(null, events);
        }
        SessionState baseState = deserialize(base.get().getState()).toSessionState();
        List<SessionEvent> remaining = eventRepository
                .findBySessionIdAndServerSequenceGreaterThanAndOccurredAtLessThanEqualOrderByServerSequenceAsc(
                        sessionId, baseState.getUpToSequence(), at);
        return reducer.reduce(baseState, remaining);
    }

    private TimelineResponse toResponse(Instant at, SessionState state, int messageLimit) {
        List<TimelineParticipant> participants = new ArrayList<>();
        state.getParticipants().forEach((userId, p) -> {
            if (!p.isLeft()) {
                participants.add(new TimelineParticipant(userId, p.getPresence(), p.getJoinedAt()));
            }
        });
        return new TimelineResponse(at, state.getUpToSequence(), participants, lastN(state.getMessages(), messageLimit));
    }

    private List<TimelineMessage> lastN(List<TimelineMessage> messages, int n) {
        return messages.size() <= n ? messages : messages.subList(messages.size() - n, messages.size());
    }

    private SnapshotState deserialize(String json) {
        try {
            return objectMapper.readValue(json, SnapshotState.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("스냅샷 역직렬화 실패: 포맷 손상 의심", e);
        }
    }
}
