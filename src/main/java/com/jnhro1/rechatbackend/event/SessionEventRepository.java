package com.jnhro1.rechatbackend.event;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionEventRepository extends JpaRepository<SessionEvent, Long> {

    Optional<SessionEvent> findBySessionIdAndEventId(Long sessionId, String eventId);

    /**
     * occurred_at이 [from, to] 범위인 이벤트를 server_sequence 오름차순으로 조회(디버깅/검증/리플레이).
     * from/to는 선택(null이면 해당 경계 무시). 필터는 idx_event_session_occurred(session_id, occurred_at),
     * 정렬·결정성은 server_sequence(명세 §7.1 #2). limit은 Pageable로 안전 상한.
     */
    @Query("""
            SELECT e FROM SessionEvent e
            WHERE e.sessionId = :sessionId
              AND (:from IS NULL OR e.occurredAt >= :from)
              AND (:to IS NULL OR e.occurredAt <= :to)
            ORDER BY e.serverSequence ASC
            """)
    List<SessionEvent> findInOccurredRange(
            @Param("sessionId") Long sessionId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    /**
     * 특정 시점(occurredAt) 이전의 이벤트를 server_sequence 오름차순으로 조회(상태 복원 리플레이).
     * 명세 §7.1 쿼리 #2. 필터는 idx_event_session_occurred(session_id, occurred_at), 정렬은 server_sequence.
     */
    List<SessionEvent> findBySessionIdAndOccurredAtLessThanEqualOrderByServerSequenceAsc(
            Long sessionId, Instant at);

    /**
     * 스냅샷 base(afterSequence) 이후이면서 occurred_at<=at인 이벤트만 server_sequence 순으로 조회.
     * 과거 시점 복원의 증분 보정 — base 스냅샷 위에 이 결과만 리플레이하면 full replay와 동일한 상태가 된다.
     */
    List<SessionEvent> findBySessionIdAndServerSequenceGreaterThanAndOccurredAtLessThanEqualOrderByServerSequenceAsc(
            Long sessionId, long afterSequence, Instant at);

    /**
     * 특정 시퀀스 이후의 이벤트를 server_sequence 오름차순으로 조회(커서 페이지네이션·리플레이).
     * uk_event_session_seq(session_id, server_sequence) 인덱스를 활용하는 핫패스 쿼리.
     */
    List<SessionEvent> findBySessionIdAndServerSequenceGreaterThanOrderByServerSequenceAsc(
            Long sessionId, long afterSequence, Pageable pageable);

    /** 스냅샷 이후 전체 이벤트(무제한) — 현재상태 복원의 증분 리플레이. */
    List<SessionEvent> findBySessionIdAndServerSequenceGreaterThanOrderByServerSequenceAsc(
            Long sessionId, long afterSequence);

    /** 특정 시퀀스 이하의 이벤트 — 스냅샷 생성 시 1..uptoSequence fold. */
    List<SessionEvent> findBySessionIdAndServerSequenceLessThanEqualOrderByServerSequenceAsc(
            Long sessionId, long uptoSequence);
}
