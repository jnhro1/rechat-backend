package com.jnhro1.rechatbackend.participant;

import com.jnhro1.rechatbackend.common.entity.BaseEntity;
import com.jnhro1.rechatbackend.participant.enums.PresenceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
        name = "session_participants",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_participant_session_user",
                columnNames = {"session_id", "user_id"}
        ),
        indexes = @Index(name = "idx_participant_session", columnList = "session_id")
)
@Comment("세션 참여자 + Presence 상태")
public class SessionParticipant extends BaseEntity {

    // FYI: 이벤트 스토어 볼륨/조회 성능을 고려해 자식 엔티티는 JPA 연관 대신 sessionId 컬럼만 보유.
    //      FK 제약은 DDL(Flyway)에서 관리.
    @Column(name = "session_id", nullable = false)
    @Comment("소속 세션 FK")
    private Long sessionId;

    @Column(name = "user_id", nullable = false, length = 64)
    @Comment("외부 사용자 식별자")
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "presence", nullable = false, length = 16)
    @Comment("접속 상태: ONLINE/OFFLINE")
    private PresenceStatus presence;

    @Column(name = "joined_at", nullable = false)
    @Comment("입장 시각")
    private Instant joinedAt;

    @Column(name = "left_at")
    @Comment("퇴장 시각 (미퇴장 시 null)")
    private Instant leftAt;

    @Column(name = "last_seen_at", nullable = false)
    @Comment("마지막 접속 시각")
    private Instant lastSeenAt;

    public static SessionParticipant join(Long sessionId, String userId, Instant now) {
        return SessionParticipant.builder()
                .sessionId(sessionId)
                .userId(userId)
                .presence(PresenceStatus.ONLINE)
                .joinedAt(now)
                .lastSeenAt(now)
                .build();
    }

    public void leave(Instant now) {
        this.presence = PresenceStatus.OFFLINE;
        this.leftAt = now;
        this.lastSeenAt = now;
    }

    public void disconnect(Instant now) {
        this.presence = PresenceStatus.OFFLINE;
        this.lastSeenAt = now;
    }

    public void reconnect(Instant now) {
        this.presence = PresenceStatus.ONLINE;
        this.lastSeenAt = now;
    }

    public void rejoin(Instant now) {
        this.presence = PresenceStatus.ONLINE;
        this.leftAt = null;
        this.joinedAt = now;
        this.lastSeenAt = now;
    }
}
