package com.jnhro1.rechatbackend.session;

import com.jnhro1.rechatbackend.session.enums.SessionStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionRepository extends JpaRepository<Session, Long> {

    /**
     * 세션 행에 비관적 쓰기 락(SELECT ... FOR UPDATE)을 걸고 조회한다.
     * join 시 같은 세션의 동시 참여를 직렬화해 정원(1:1) 검증을 정확히 하기 위함(CONVENTIONS §10).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Session s WHERE s.id = :id")
    Optional<Session> findByIdForUpdate(@Param("id") Long id);

    /**
     * 세션 목록 동적 필터 조회. 각 파라미터가 null이면 해당 필터는 비적용된다.
     * 참여자 필터는 session_participants의 {@code uk_participant_session_user} 유니크 인덱스를 활용하는 EXISTS 서브쿼리.
     * 정렬(ORDER BY)은 JPQL에 두지 않고 {@link Pageable}의 Sort가 주입한다.
     */
    @Query(value = """
            SELECT s FROM Session s
            WHERE (:status IS NULL OR s.status = :status)
              AND (:from IS NULL OR s.createdAt >= :from)
              AND (:to IS NULL OR s.createdAt <= :to)
              AND (:participantId IS NULL OR EXISTS (
                    SELECT 1 FROM SessionParticipant p
                    WHERE p.sessionId = s.id AND p.userId = :participantId))
            """,
            countQuery = """
            SELECT COUNT(s) FROM Session s
            WHERE (:status IS NULL OR s.status = :status)
              AND (:from IS NULL OR s.createdAt >= :from)
              AND (:to IS NULL OR s.createdAt <= :to)
              AND (:participantId IS NULL OR EXISTS (
                    SELECT 1 FROM SessionParticipant p
                    WHERE p.sessionId = s.id AND p.userId = :participantId))
            """)
    Page<Session> search(
            @Param("status") SessionStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("participantId") String participantId,
            Pageable pageable
    );
}
