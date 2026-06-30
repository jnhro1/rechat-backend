package com.jnhro1.rechatbackend.restore;
import com.jnhro1.rechatbackend.session.SessionRepository;
import com.jnhro1.rechatbackend.event.SessionEvent;
import com.jnhro1.rechatbackend.event.SessionEventRepository;
import com.jnhro1.rechatbackend.restore.snapshot.SessionSnapshotRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jnhro1.rechatbackend.event.enums.EventType;
import com.jnhro1.rechatbackend.participant.enums.PresenceStatus;
import com.jnhro1.rechatbackend.session.exception.SessionNotFoundException;
import com.jnhro1.rechatbackend.restore.response.TimelineResponse;
import com.jnhro1.rechatbackend.restore.snapshot.SessionSnapshot;
import com.jnhro1.rechatbackend.restore.snapshot.SnapshotState;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionTimelineServiceTest {

    private static final Long SESSION_ID = 1L;
    private static final Instant T0 = Instant.parse("2026-06-28T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-06-28T00:00:01Z");
    private static final Instant T2 = Instant.parse("2026-06-28T00:00:02Z");
    private static final Instant T3 = Instant.parse("2026-06-28T00:00:03Z");
    private static final Instant AT = Instant.parse("2026-06-28T01:00:00Z");

    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private SessionEventRepository eventRepository;
    @Mock
    private SessionSnapshotRepository snapshotRepository;

    private ObjectMapper objectMapper;
    private SessionTimelineService sessionTimelineService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Clock clock = Clock.fixed(AT, ZoneOffset.UTC);
        sessionTimelineService = new SessionTimelineService(
                sessionRepository, eventRepository, snapshotRepository,
                new SessionStateReducer(objectMapper), objectMapper, clock);
    }

    private SessionEvent message(long seq, String content, Instant occurredAt) {
        return SessionEvent.of(SESSION_ID, seq, "m" + seq, "alice",
                EventType.MESSAGE, "{\"content\":\"%s\"}".formatted(content), occurredAt);
    }

    private SessionEvent participantEvent(long seq, EventType type, String userId, Instant occurredAt) {
        return SessionEvent.of(SESSION_ID, seq, "p" + seq, userId, type, null, occurredAt);
    }

    /** at != null 경로(full replay)의 occurredAt 쿼리를 스텁한다. */
    private void stubAtQuery(List<SessionEvent> events) {
        when(sessionRepository.existsById(SESSION_ID)).thenReturn(true);
        when(eventRepository.findBySessionIdAndOccurredAtLessThanEqualOrderByServerSequenceAsc(eq(SESSION_ID), any()))
                .thenReturn(events);
    }

    @Test
    @DisplayName("reconstruct(at) - JOIN + MESSAGE 이벤트를 fold해 참여자와 메시지를 복원한다")
    void reconstruct_foldsParticipantsAndMessages() {
        stubAtQuery(List.of(
                participantEvent(1, EventType.JOIN, "alice", T0),
                message(2, "hi", T1),
                message(3, "bye", T2)));

        TimelineResponse response = sessionTimelineService.reconstruct(SESSION_ID, AT, 50);

        assertThat(response.messages()).extracting("content").containsExactly("hi", "bye");
        assertThat(response.participants()).extracting("userId").containsExactly("alice");
        assertThat(response.participants().get(0).presence()).isEqualTo(PresenceStatus.ONLINE);
        assertThat(response.upToSequence()).isEqualTo(3L);
    }

    @Test
    @DisplayName("reconstruct(at) - LEAVE한 참여자는 복원 결과에서 제외된다")
    void reconstruct_excludesLeftParticipant() {
        stubAtQuery(List.of(
                participantEvent(1, EventType.JOIN, "alice", T0),
                participantEvent(2, EventType.JOIN, "bob", T1),
                participantEvent(3, EventType.LEAVE, "bob", T2)));

        TimelineResponse response = sessionTimelineService.reconstruct(SESSION_ID, AT, 50);

        assertThat(response.participants()).extracting("userId").containsExactly("alice");
    }

    @Test
    @DisplayName("reconstruct(at) - DISCONNECT가 시점 presence(OFFLINE)에 반영된다")
    void reconstruct_foldsPresence() {
        stubAtQuery(List.of(
                participantEvent(1, EventType.JOIN, "alice", T0),
                participantEvent(2, EventType.DISCONNECT, "alice", T1)));

        TimelineResponse response = sessionTimelineService.reconstruct(SESSION_ID, AT, 50);

        assertThat(response.participants().get(0).presence()).isEqualTo(PresenceStatus.OFFLINE);
    }

    @Test
    @DisplayName("reconstruct(at) - messageLimit으로 최근 N개만 반환한다")
    void reconstruct_appliesMessageLimit() {
        stubAtQuery(List.of(
                participantEvent(1, EventType.JOIN, "alice", T0),
                message(2, "a", T1), message(3, "b", T2), message(4, "c", T3)));

        TimelineResponse response = sessionTimelineService.reconstruct(SESSION_ID, AT, 2);

        assertThat(response.messages()).extracting("content").containsExactly("b", "c");
        assertThat(response.upToSequence()).isEqualTo(4L);
    }

    @Test
    @DisplayName("reconstruct(at) - 안전한 스냅샷이 있으면 base + 이후 이벤트만 보정 리플레이(full 쿼리 미사용)")
    void reconstruct_usesSnapshotBase_whenSafe() throws Exception {
        // base 스냅샷: seq1(JOIN alice @T0)까지, watermark=T0
        SessionState baseState = new SessionStateReducer(objectMapper)
                .reduce(null, List.of(participantEvent(1, EventType.JOIN, "alice", T0)));
        String stateJson = objectMapper.writeValueAsString(SnapshotState.from(baseState));
        SessionSnapshot snapshot = SessionSnapshot.of(SESSION_ID, 1L, stateJson, 1, T0);

        when(sessionRepository.existsById(SESSION_ID)).thenReturn(true);
        when(snapshotRepository
                .findTopBySessionIdAndMaxOccurredAtLessThanEqualOrderByUptoSequenceDesc(eq(SESSION_ID), any()))
                .thenReturn(Optional.of(snapshot));
        when(eventRepository
                .findBySessionIdAndServerSequenceGreaterThanAndOccurredAtLessThanEqualOrderByServerSequenceAsc(
                        eq(SESSION_ID), eq(1L), any()))
                .thenReturn(List.of(message(2, "hi", T1)));

        TimelineResponse response = sessionTimelineService.reconstruct(SESSION_ID, AT, 50);

        assertThat(response.participants()).extracting("userId").containsExactly("alice");
        assertThat(response.messages()).extracting("content").containsExactly("hi");
        assertThat(response.upToSequence()).isEqualTo(2L);
        // full replay 쿼리는 타지 않아야 한다(스냅샷 가속 경로)
        verify(eventRepository, never())
                .findBySessionIdAndOccurredAtLessThanEqualOrderByServerSequenceAsc(any(), any());
    }

    @Test
    @DisplayName("reconstruct(at) - 안전한 스냅샷이 없으면 full replay 폴백(보정 쿼리 미사용)")
    void reconstruct_fullReplay_whenNoSafeSnapshot() {
        when(sessionRepository.existsById(SESSION_ID)).thenReturn(true);
        when(snapshotRepository
                .findTopBySessionIdAndMaxOccurredAtLessThanEqualOrderByUptoSequenceDesc(eq(SESSION_ID), any()))
                .thenReturn(Optional.empty());
        when(eventRepository.findBySessionIdAndOccurredAtLessThanEqualOrderByServerSequenceAsc(eq(SESSION_ID), any()))
                .thenReturn(List.of(
                        participantEvent(1, EventType.JOIN, "alice", T0),
                        message(2, "hi", T1)));

        TimelineResponse response = sessionTimelineService.reconstruct(SESSION_ID, AT, 50);

        assertThat(response.messages()).extracting("content").containsExactly("hi");
        verify(eventRepository, never())
                .findBySessionIdAndServerSequenceGreaterThanAndOccurredAtLessThanEqualOrderByServerSequenceAsc(
                        any(), anyLong(), any());
    }

    @Test
    @DisplayName("reconstruct - 세션 없음 시 SessionNotFoundException")
    void reconstruct_throwsWhenSessionMissing() {
        when(sessionRepository.existsById(SESSION_ID)).thenReturn(false);

        assertThatThrownBy(() -> sessionTimelineService.reconstruct(SESSION_ID, AT, 50))
                .isInstanceOf(SessionNotFoundException.class);
    }
}
