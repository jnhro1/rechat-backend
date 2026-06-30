package com.jnhro1.rechatbackend.restore.snapshot;

import com.jnhro1.rechatbackend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
        name = "session_snapshots",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_snapshot_session_upto",
                columnNames = {"session_id", "upto_sequence"}
        )
)
@Comment("세션 상태 스냅샷")
public class SessionSnapshot extends BaseEntity {

    @Column(name = "session_id", nullable = false)
    @Comment("소속 세션 FK")
    private Long sessionId;

    @Column(name = "upto_sequence", nullable = false)
    @Comment("이 서버 시퀀스까지 반영된 상태")
    private long uptoSequence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "state", columnDefinition = "json", nullable = false)
    @Comment("직렬화된 세션 상태(JSON)")
    private String state;

    // FYI: 스냅샷 포맷 버전 — 포맷 변경 감지 및 Snapshot-EventStore 불일치 검증(명세 7.3.3)에 사용.
    @Column(name = "version", nullable = false)
    @Comment("스냅샷 포맷 버전")
    private int version;

    // FYI: 커버한 이벤트 중 최대 occurred_at. 과거 시점(at) 복원 시 이 base를 안전하게 쓸 수 있는지(watermark<=at) 판단.
    @Column(name = "max_occurred_at", nullable = false)
    @Comment("커버한 이벤트 중 최대 occurred_at(watermark)")
    private Instant maxOccurredAt;

    public static SessionSnapshot of(Long sessionId, long uptoSequence, String state, int version, Instant maxOccurredAt) {
        return SessionSnapshot.builder()
                .sessionId(sessionId)
                .uptoSequence(uptoSequence)
                .state(state)
                .version(version)
                .maxOccurredAt(maxOccurredAt)
                .build();
    }
}
