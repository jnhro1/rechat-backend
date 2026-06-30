package com.jnhro1.rechatbackend.event.message;

import com.jnhro1.rechatbackend.common.entity.BaseEntity;
import com.jnhro1.rechatbackend.event.message.MessageStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
        name = "session_messages",
        // FYI: (session_id, server_sequence)는 메시지 식별이자 최근 N개 조회의 정렬 인덱스를 겸한다.
        uniqueConstraints = @UniqueConstraint(
                name = "uk_message_session_seq",
                columnNames = {"session_id", "server_sequence"}
        )
)
@Comment("메시지 조회 모델 (projection)")
public class SessionMessage extends BaseEntity {

    @Column(name = "session_id", nullable = false)
    @Comment("소속 세션 FK")
    private Long sessionId;

    @Column(name = "server_sequence", nullable = false)
    @Comment("생성 이벤트 서버 시퀀스 (메시지 식별·정렬 키)")
    private long serverSequence;

    @Column(name = "sender_id", nullable = false, length = 64)
    @Comment("보낸 참여자")
    private String senderId;

    @Column(name = "content", length = 1000)
    @Comment("메시지 본문 (삭제 시 null)")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    @Comment("메시지 상태: SENT/EDITED/DELETED")
    private MessageStatus status;

    @Column(name = "edited_at")
    @Comment("수정 시각")
    private Instant editedAt;

    @Column(name = "deleted_at")
    @Comment("삭제 시각")
    private Instant deletedAt;

    public static SessionMessage send(Long sessionId, long serverSequence, String senderId, String content) {
        return SessionMessage.builder()
                .sessionId(sessionId)
                .serverSequence(serverSequence)
                .senderId(senderId)
                .content(content)
                .status(MessageStatus.SENT)
                .build();
    }

    public void edit(String content, Instant now) {
        this.content = content;
        this.status = MessageStatus.EDITED;
        this.editedAt = now;
    }

    public void delete(Instant now) {
        this.content = null;
        this.status = MessageStatus.DELETED;
        this.deletedAt = now;
    }
}
