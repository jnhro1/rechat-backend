package com.jnhro1.rechatbackend.restore.snapshot;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionSnapshotRepository extends JpaRepository<SessionSnapshot, Long> {

    Optional<SessionSnapshot> findTopBySessionIdOrderByUptoSequenceDesc(Long sessionId);

    /**
     * 과거 시점(at) 복원의 base — watermark(max_occurred_at)가 at 이하인 스냅샷 중 가장 진행된 것.
     * watermark<=at 보장으로 base가 fold한 이벤트가 모두 at 시점에 이미 발생했음 → 안전하게 재사용 가능.
     * idx_snapshot_session_watermark(session_id, max_occurred_at) 활용.
     */
    Optional<SessionSnapshot> findTopBySessionIdAndMaxOccurredAtLessThanEqualOrderByUptoSequenceDesc(
            Long sessionId, Instant at);

    boolean existsBySessionIdAndUptoSequence(Long sessionId, long uptoSequence);
}
