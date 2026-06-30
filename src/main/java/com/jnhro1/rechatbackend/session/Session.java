package com.jnhro1.rechatbackend.session;

import com.jnhro1.rechatbackend.common.entity.BaseEntity;
import com.jnhro1.rechatbackend.session.enums.SessionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Entity
@Table(
        name = "sessions",
        indexes = @Index(name = "idx_sessions_status_created", columnList = "status, created_at")
)
@Comment("1:1 채팅 세션")
public class Session extends BaseEntity {

    /** 1:1 채팅 정원 — 한 세션 최대 참여자 수. */
    public static final int MAX_PARTICIPANTS = 2;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Comment("세션 상태: ACTIVE/INTERRUPTED/COMPLETED")
    private SessionStatus status;

    @Column(name = "started_at", nullable = false)
    @Comment("세션 시작 시각")
    private Instant startedAt;

    @Column(name = "ended_at")
    @Comment("세션 종료 시각 (미종료 시 null)")
    private Instant endedAt;

    // FYI: 서버 시퀀스 발급기. 실제 채번은 리포지토리의 원자적 조건부 UPDATE로 처리해
    //      동시 수집 시에도 세션 내 충돌 없는 단조 증가를 보장한다(순서·결정성의 기준).
    @Column(name = "last_sequence", nullable = false)
    @Comment("세션 내 서버 시퀀스 발급기 (단조 증가)")
    private long lastSequence;

    public static Session start(Instant now) {
        return Session.builder()
                .status(SessionStatus.ACTIVE)
                .startedAt(now)
                .lastSequence(0L)
                .build();
    }

    public void complete(Instant now) {
        this.status = SessionStatus.COMPLETED;
        this.endedAt = now;
    }

    public void interrupt() {
        this.status = SessionStatus.INTERRUPTED;
    }

    /**
     * 세션 내 다음 서버 시퀀스를 채번한다(단조 증가).
     * FYI: 동시 채번 충돌을 막기 위해 반드시 세션 행 비관적 락(findByIdForUpdate) 안에서 호출해야 한다.
     */
    public long allocateSequence() {
        return ++this.lastSequence;
    }

    public boolean isActive() {
        return this.status == SessionStatus.ACTIVE;
    }
}
