package com.jnhro1.rechatbackend.event;
import com.jnhro1.rechatbackend.session.Session;

import com.jnhro1.rechatbackend.event.enums.EventType;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SessionEventAppender {

    private final SessionEventRepository eventRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AppendResult append(
            Session session,
            String eventId,
            String senderId,
            EventType type,
            String payload,
            Instant occurredAt
    ) {
        Optional<SessionEvent> duplicate = eventRepository.findBySessionIdAndEventId(session.getId(), eventId);
        if (duplicate.isPresent()) {
            return new AppendResult(false, duplicate.get());
        }
        long serverSequence = session.allocateSequence();
        SessionEvent saved = eventRepository.save(SessionEvent.of(
                session.getId(), serverSequence, eventId, senderId, type, payload, occurredAt));
        // 커밋 후 브로드캐스트를 위한 신호. 전송은 @TransactionalEventListener(AFTER_COMMIT)가 담당.
        eventPublisher.publishEvent(new SessionEventAppended(saved));
        return new AppendResult(true, saved);
    }

    public record AppendResult(boolean created, SessionEvent event) {
    }
}
