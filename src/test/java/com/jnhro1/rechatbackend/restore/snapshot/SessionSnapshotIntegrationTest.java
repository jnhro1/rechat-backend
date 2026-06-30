package com.jnhro1.rechatbackend.restore.snapshot;
import com.jnhro1.rechatbackend.session.Session;
import com.jnhro1.rechatbackend.session.SessionRepository;
import com.jnhro1.rechatbackend.participant.SessionParticipantRepository;
import com.jnhro1.rechatbackend.participant.SessionParticipantService;
import com.jnhro1.rechatbackend.event.SessionEvent;
import com.jnhro1.rechatbackend.event.SessionEventRepository;
import com.jnhro1.rechatbackend.event.SessionEventService;
import com.jnhro1.rechatbackend.event.message.SessionMessageRepository;
import com.jnhro1.rechatbackend.restore.SessionTimelineService;

import static org.assertj.core.api.Assertions.assertThat;

import com.jnhro1.rechatbackend.event.enums.EventType;
import com.jnhro1.rechatbackend.participant.enums.PresenceStatus;
import com.jnhro1.rechatbackend.event.request.CollectEventRequest;
import com.jnhro1.rechatbackend.restore.response.TimelineResponse;
import com.jnhro1.rechatbackend.support.IntegrationTestBase;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SessionSnapshotIntegrationTest extends IntegrationTestBase {

    private static final Instant NOW = Instant.parse("2026-06-29T00:00:00Z");

    @Autowired
    private SessionParticipantService participantService;
    @Autowired
    private SessionEventService eventService;
    @Autowired
    private SessionSnapshotService snapshotService;
    @Autowired
    private SessionTimelineService timelineService;
    @Autowired
    private SessionRepository sessionRepository;
    @Autowired
    private SessionSnapshotRepository snapshotRepository;
    @Autowired
    private SessionParticipantRepository participantRepository;
    @Autowired
    private SessionEventRepository eventRepository;
    @Autowired
    private SessionMessageRepository messageRepository;

    @AfterEach
    void tearDown() {
        snapshotRepository.deleteAllInBatch();
        messageRepository.deleteAllInBatch();
        eventRepository.deleteAllInBatch();
        participantRepository.deleteAllInBatch();
        sessionRepository.deleteAllInBatch();
    }

    private Long newSession() {
        return sessionRepository.save(Session.start(NOW)).getId();
    }

    private void collectMessage(Long sessionId, String eventId, String content) {
        eventService.collect(sessionId,
                new CollectEventRequest(eventId, EventType.MESSAGE, "alice", content, NOW));
    }

    /** 원장에 이벤트를 직접 적재한다(occurred_at·server_sequence를 결정적으로 제어 — async/실시계 비의존). */
    private void appendEvent(Long sessionId, long seq, EventType type, String sender, String content, Instant occurredAt) {
        String payload = (type == EventType.MESSAGE) ? "{\"content\":\"%s\"}".formatted(content) : null;
        eventRepository.save(SessionEvent.of(sessionId, seq, "e" + seq, sender, type, payload, occurredAt));
    }

    /** interval(테스트=3) 배수 시퀀스 도달까지 폴링으로 비동기 스냅샷 생성을 기다린다. */
    private SessionSnapshot awaitSnapshot(Long sessionId, long uptoSequence) throws Exception {
        for (int i = 0; i < 50; i++) {
            var snapshot = snapshotRepository.findTopBySessionIdOrderByUptoSequenceDesc(sessionId);
            if (snapshot.isPresent() && snapshot.get().getUptoSequence() >= uptoSequence) {
                return snapshot.get();
            }
            Thread.sleep(100);
        }
        throw new AssertionError("스냅샷이 생성되지 않음(uptoSequence=" + uptoSequence + ")");
    }

    @Test
    @DisplayName("스냅샷 자동 생성 - interval 배수 이벤트에서 비동기로 스냅샷이 생성된다")
    void autoCreatesSnapshot_atInterval() throws Exception {
        Long sessionId = newSession();
        participantService.join(sessionId, "alice", "j1"); // seq 1 JOIN
        participantService.join(sessionId, "bob", "j2");   // seq 2 JOIN
        collectMessage(sessionId, "e3", "hi");             // seq 3 MESSAGE → interval(3) 도달 → 스냅샷

        SessionSnapshot snapshot = awaitSnapshot(sessionId, 3);

        assertThat(snapshot.getUptoSequence()).isEqualTo(3L);
    }

    @Test
    @DisplayName("스냅샷 가속 결과 == full replay 결과 (정확성·결정성)")
    void snapshotAccelerated_equalsFullReplay() throws Exception {
        Long sessionId = newSession();
        participantService.join(sessionId, "alice", "j1"); // seq1
        participantService.join(sessionId, "bob", "j2");   // seq2
        collectMessage(sessionId, "e3", "a");              // seq3 → 스냅샷 트리거
        awaitSnapshot(sessionId, 3);
        collectMessage(sessionId, "e4", "b");              // seq4 (스냅샷 이후)
        collectMessage(sessionId, "e5", "c");              // seq5

        // 스냅샷 가속 경로 (현재상태)
        TimelineResponse accelerated = timelineService.reconstruct(sessionId, null, 50);

        // 스냅샷 제거 후 full replay 경로
        snapshotRepository.deleteAllInBatch();
        TimelineResponse fullReplay = timelineService.reconstruct(sessionId, null, 50);

        assertThat(accelerated.upToSequence()).isEqualTo(5L);
        assertThat(accelerated.upToSequence()).isEqualTo(fullReplay.upToSequence());
        assertThat(accelerated.participants()).isEqualTo(fullReplay.participants());
        assertThat(accelerated.messages()).isEqualTo(fullReplay.messages());
        assertThat(accelerated.messages()).extracting("content").containsExactly("a", "b", "c");
        assertThat(accelerated.participants()).extracting("userId").containsExactlyInAnyOrder("alice", "bob");
        assertThat(accelerated.participants()).allMatch(p -> p.presence() == PresenceStatus.ONLINE);
    }

    @Test
    @DisplayName("과거 시점(at) - 스냅샷 가속 결과 == full replay 결과 (순서 역전 포함, 결정성)")
    void pastTimeSnapshotAccelerated_equalsFullReplay() {
        Long sessionId = newSession();
        Instant t0 = NOW;
        appendEvent(sessionId, 1, EventType.JOIN, "alice", null, t0);
        appendEvent(sessionId, 2, EventType.JOIN, "bob", null, t0);
        appendEvent(sessionId, 3, EventType.MESSAGE, "alice", "a", t0.plusSeconds(10));
        snapshotService.create(sessionId, 3);                                  // watermark = t0+10s
        appendEvent(sessionId, 4, EventType.MESSAGE, "alice", "b", t0.plusSeconds(20));
        appendEvent(sessionId, 5, EventType.MESSAGE, "alice", "c", t0.plusSeconds(30));

        Instant at = t0.plusSeconds(25); // seq1~4 포함, seq5 제외
        assertThat(snapshotRepository.findTopBySessionIdAndMaxOccurredAtLessThanEqualOrderByUptoSequenceDesc(sessionId, at))
                .as("watermark<=at 이므로 스냅샷이 base로 선택돼야 함").isPresent();

        // 스냅샷 가속 경로
        TimelineResponse accelerated = timelineService.reconstruct(sessionId, at, 50);
        // 스냅샷 제거 후 동일 시점 full replay
        snapshotRepository.deleteAllInBatch();
        TimelineResponse fullReplay = timelineService.reconstruct(sessionId, at, 50);

        assertThat(accelerated.upToSequence()).isEqualTo(4L);
        assertThat(accelerated.upToSequence()).isEqualTo(fullReplay.upToSequence());
        assertThat(accelerated.participants()).isEqualTo(fullReplay.participants());
        assertThat(accelerated.messages()).isEqualTo(fullReplay.messages());
        assertThat(accelerated.messages()).extracting("content").containsExactly("a", "b");
    }

    @Test
    @DisplayName("과거 시점(at) - watermark>at인 스냅샷은 base로 쓰이지 않아 미래 발생 이벤트가 새지 않는다")
    void pastTime_skipsSnapshot_whenWatermarkExceedsAt() {
        Long sessionId = newSession();
        Instant t0 = NOW;
        appendEvent(sessionId, 1, EventType.JOIN, "alice", null, t0);
        appendEvent(sessionId, 2, EventType.MESSAGE, "alice", "early", t0.plusSeconds(5));
        appendEvent(sessionId, 3, EventType.MESSAGE, "alice", "late-arrival", t0.plusSeconds(100)); // 미래 발생, seq3로 도착
        snapshotService.create(sessionId, 3);                                  // watermark = t0+100s
        appendEvent(sessionId, 4, EventType.MESSAGE, "alice", "mid", t0.plusSeconds(10));           // 순서 역전: seq4지만 발생은 빠름

        Instant at = t0.plusSeconds(20); // early(5)·mid(10) 포함, late-arrival(100) 제외
        // 스냅샷 자체는 존재하지만 watermark(t0+100s) > at 라 base로 선택되면 안 된다.
        assertThat(snapshotRepository.findTopBySessionIdOrderByUptoSequenceDesc(sessionId)).isPresent();
        assertThat(snapshotRepository.findTopBySessionIdAndMaxOccurredAtLessThanEqualOrderByUptoSequenceDesc(sessionId, at))
                .as("watermark>at 스냅샷은 후보에서 제외").isEmpty();

        TimelineResponse response = timelineService.reconstruct(sessionId, at, 50);

        // 미래 발생(late-arrival)이 과거 복원에 새지 않음 — 정합성
        assertThat(response.messages()).extracting("content").containsExactly("early", "mid");
        assertThat(response.upToSequence()).isEqualTo(4L);
    }
}
