package com.jnhro1.rechatbackend.participant;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionParticipantRepository extends JpaRepository<SessionParticipant, Long> {

    long countBySessionId(Long sessionId);

    /** 현재 세션에 '존재하는'(퇴장하지 않은) 참여자 수 — 1:1 정원 검사 기준. */
    long countBySessionIdAndLeftAtIsNull(Long sessionId);

    List<SessionParticipant> findBySessionId(Long sessionId);

    Optional<SessionParticipant> findBySessionIdAndUserId(Long sessionId, String userId);

    /** 특정 시점(t)까지 참여한 참여자 — 상태 복원의 시점 멤버십(joinedAt <= t). */
    List<SessionParticipant> findBySessionIdAndJoinedAtLessThanEqual(Long sessionId, Instant at);
}
