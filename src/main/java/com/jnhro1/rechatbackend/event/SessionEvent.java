package com.jnhro1.rechatbackend.event;

import com.jnhro1.rechatbackend.common.entity.BaseEntity;
import com.jnhro1.rechatbackend.event.enums.EventType;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Entity
@Table(
        name = "session_events",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_event_session_eventid",
                        columnNames = {"session_id", "event_id"}
                ),
                @UniqueConstraint(
                        name = "uk_event_session_seq",
                        columnNames = {"session_id", "server_sequence"}
                )
        },
        // FYI: 특정 시점(occurred_at) 이전 이벤트 조회용 보조 인덱스. 순차 조회는 위 uk_event_session_seq가 커버.
        indexes = @Index(name = "idx_event_session_occurred", columnList = "session_id, occurred_at")
)
@Comment("세션 이벤트 원장 (append-only)")
public class SessionEvent extends BaseEntity {

    @Column(name = "session_id", nullable = false)
    @Comment("소속 세션 FK")
    private Long sessionId;

    @Column(name = "server_sequence", nullable = false)
    @Comment("세션 내 서버 시퀀스 (정렬·복원 기준)")
    private long serverSequence;

    @Column(name = "event_id", nullable = false, length = 64)
    @Comment("클라이언트 생성 멱등키(UUID) — 중복 차단")
    private String eventId;

    @Column(name = "sender_id", nullable = false, length = 64)
    @Comment("이벤트를 발생시킨 참여자")
    private String senderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    @Comment("이벤트 종류: MESSAGE/JOIN/LEAVE/DISCONNECT/RECONNECT/EDIT/DELETE")
    private EventType type;

    // FYI: 타입별 가변 데이터(메시지 본문, EDIT/DELETE 대상 targetSequence 등)를 JSON으로 단일화.
    //      이벤트 타입별 테이블 분리 대신 단일 테이블 + JSON → 리플레이 단순화. 트레이드오프는 문서화.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "json")
    @Comment("타입별 가변 데이터(JSON)")
    private String payload;

    @Column(name = "occurred_at", nullable = false)
    @Comment("클라이언트 발생 시각")
    private Instant occurredAt;

    /**
     * 서버 수신 시각은 BaseEntity.createdAt이 담당한다.
     */
    public static SessionEvent of(
            Long sessionId,
            long serverSequence,
            String eventId,
            String senderId,
            EventType type,
            String payload,
            Instant occurredAt
    ) {
        return SessionEvent.builder()
                .sessionId(sessionId)
                .serverSequence(serverSequence)
                .eventId(eventId)
                .senderId(senderId)
                .type(type)
                .payload(payload)
                .occurredAt(occurredAt)
                .build();
    }
}
