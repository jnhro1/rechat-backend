package com.jnhro1.rechatbackend.event;
import com.jnhro1.rechatbackend.session.Session;
import com.jnhro1.rechatbackend.session.SessionRepository;
import com.jnhro1.rechatbackend.participant.SessionParticipantRepository;
import com.jnhro1.rechatbackend.event.message.SessionMessage;
import com.jnhro1.rechatbackend.event.message.SessionMessageRepository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jnhro1.rechatbackend.event.enums.EventType;
import com.jnhro1.rechatbackend.event.exception.SenderNotParticipantException;
import com.jnhro1.rechatbackend.session.exception.SessionNotActiveException;
import com.jnhro1.rechatbackend.session.exception.SessionNotFoundException;
import com.jnhro1.rechatbackend.event.exception.UnsupportedEventTypeException;
import com.jnhro1.rechatbackend.event.payload.MessagePayload;
import com.jnhro1.rechatbackend.event.request.CollectEventRequest;
import com.jnhro1.rechatbackend.event.response.EventRangeResponse;
import com.jnhro1.rechatbackend.event.response.EventSliceResponse;
import com.jnhro1.rechatbackend.event.response.SessionEventResponse;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SessionEventService {

    private final SessionRepository sessionRepository;
    private final SessionEventRepository eventRepository;
    private final SessionParticipantRepository participantRepository;
    private final SessionMessageRepository messageRepository;
    private final SessionEventAppender eventAppender;
    private final ObjectMapper objectMapper;

    /**
     * 세션 행 비관적 락이 server_sequence 채번과 멱등 검사를 같은 세션 내에서 직렬화한다.
     */
    @Transactional
    public CollectEventResult collect(Long sessionId, CollectEventRequest request) {
        if (request.type() != EventType.MESSAGE) {
            throw new UnsupportedEventTypeException(request.type());
        }

        Session session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));
        if (!session.isActive()) {
            throw new SessionNotActiveException(sessionId);
        }
        if (participantRepository.findBySessionIdAndUserId(sessionId, request.senderId()).isEmpty()) {
            throw new SenderNotParticipantException(sessionId, request.senderId());
        }

        SessionEventAppender.AppendResult result = eventAppender.append(
                session,
                request.eventId(),
                request.senderId(),
                EventType.MESSAGE,
                serialize(new MessagePayload(request.content())),
                request.occurredAt()
        );
        if (result.created()) {
            messageRepository.save(SessionMessage.send(
                    sessionId, result.event().getServerSequence(), request.senderId(), request.content()));
        }
        return new CollectEventResult(result.created(), SessionEventResponse.from(result.event(), objectMapper));
    }

    @Transactional(readOnly = true)
    public EventSliceResponse getEventsAfter(Long sessionId, long afterSequence, int limit) {
        if (!sessionRepository.existsById(sessionId)) {
            throw new SessionNotFoundException(sessionId);
        }
        List<SessionEventResponse> events = eventRepository
                .findBySessionIdAndServerSequenceGreaterThanOrderByServerSequenceAsc(
                        sessionId, afterSequence, PageRequest.of(0, limit))
                .stream()
                .map(event -> SessionEventResponse.from(event, objectMapper))
                .toList();
        return EventSliceResponse.of(events, limit);
    }

    @Transactional(readOnly = true)
    public EventRangeResponse getEvents(Long sessionId, Instant from, Instant to, int limit) {
        if (!sessionRepository.existsById(sessionId)) {
            throw new SessionNotFoundException(sessionId);
        }
        List<SessionEventResponse> events = eventRepository
                .findInOccurredRange(sessionId, from, to, PageRequest.of(0, limit))
                .stream()
                .map(event -> SessionEventResponse.from(event, objectMapper))
                .toList();
        return EventRangeResponse.of(events, limit);
    }

    private String serialize(MessagePayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("이벤트 payload 직렬화 실패", e);
        }
    }
}
