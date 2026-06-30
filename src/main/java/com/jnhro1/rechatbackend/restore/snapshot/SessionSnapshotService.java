package com.jnhro1.rechatbackend.restore.snapshot;
import com.jnhro1.rechatbackend.event.SessionEvent;
import com.jnhro1.rechatbackend.event.SessionEventRepository;
import com.jnhro1.rechatbackend.restore.SessionStateReducer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jnhro1.rechatbackend.restore.snapshot.SnapshotState;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionSnapshotService {

    /** 스냅샷 포맷 버전 — 포맷 변경/불일치 감지용. */
    public static final int SNAPSHOT_VERSION = 1;

    private final SessionSnapshotRepository snapshotRepository;
    private final SessionEventRepository eventRepository;
    private final SessionStateReducer reducer;
    private final ObjectMapper objectMapper;

    @Transactional
    public void create(Long sessionId, long uptoSequence) {
        if (snapshotRepository.existsBySessionIdAndUptoSequence(sessionId, uptoSequence)) {
            return; // 멱등 — 이미 생성됨
        }
        List<SessionEvent> events = eventRepository
                .findBySessionIdAndServerSequenceLessThanEqualOrderByServerSequenceAsc(sessionId, uptoSequence);
        SnapshotState state = SnapshotState.from(reducer.reduce(null, events));
        // watermark: 커버한 이벤트 중 최대 occurred_at. (interval 배수에서만 호출되어 events 비어있지 않음)
        Instant maxOccurredAt = events.stream()
                .map(SessionEvent::getOccurredAt)
                .max(Comparator.naturalOrder())
                .orElseThrow(() -> new IllegalStateException("스냅샷 대상 이벤트 없음: session=" + sessionId));

        try {
            snapshotRepository.save(
                    SessionSnapshot.of(sessionId, uptoSequence, serialize(state), SNAPSHOT_VERSION, maxOccurredAt));
            log.debug("스냅샷 생성: session={}, uptoSequence={}", sessionId, uptoSequence);
        } catch (DataIntegrityViolationException race) {
            // 동시 생성으로 유니크 충돌 → 이미 만들어졌으므로 무시
            log.debug("스냅샷 동시 생성 무시: session={}, uptoSequence={}", sessionId, uptoSequence);
        }
    }

    private String serialize(SnapshotState state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("스냅샷 직렬화 실패", e);
        }
    }
}
