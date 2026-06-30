package com.jnhro1.rechatbackend.event;
import com.jnhro1.rechatbackend.session.Session;
import com.jnhro1.rechatbackend.session.SessionRepository;
import com.jnhro1.rechatbackend.participant.SessionParticipant;
import com.jnhro1.rechatbackend.participant.SessionParticipantRepository;
import com.jnhro1.rechatbackend.event.message.SessionMessage;
import com.jnhro1.rechatbackend.event.message.SessionMessageRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jnhro1.rechatbackend.event.enums.EventType;
import com.jnhro1.rechatbackend.event.exception.SenderNotParticipantException;
import com.jnhro1.rechatbackend.session.exception.SessionNotActiveException;
import com.jnhro1.rechatbackend.session.exception.SessionNotFoundException;
import com.jnhro1.rechatbackend.event.exception.UnsupportedEventTypeException;
import com.jnhro1.rechatbackend.event.request.CollectEventRequest;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionEventServiceTest {

    private static final Long SESSION_ID = 1L;
    private static final Instant OCCURRED = Instant.parse("2026-06-28T00:00:00Z");

    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private SessionEventRepository eventRepository;
    @Mock
    private SessionParticipantRepository participantRepository;
    @Mock
    private SessionMessageRepository messageRepository;
    @Mock
    private SessionEventAppender eventAppender;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private SessionEventService sessionEventService;

    private CollectEventRequest messageRequest() {
        return new CollectEventRequest("e1", EventType.MESSAGE, "alice", "hi", OCCURRED);
    }

    private SessionParticipant participant() {
        return SessionParticipant.join(SESSION_ID, "alice", OCCURRED);
    }

    private SessionEvent messageEvent(long seq) {
        return SessionEvent.of(SESSION_ID, seq, "e1", "alice", EventType.MESSAGE, "{\"content\":\"hi\"}", OCCURRED);
    }

    @Test
    @DisplayName("collect - MESSAGE 신규 append 시 메시지 projection 저장(created=true)")
    void collect_createsEventAndProjection() {
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(Session.start(OCCURRED)));
        when(participantRepository.findBySessionIdAndUserId(SESSION_ID, "alice")).thenReturn(Optional.of(participant()));
        when(eventAppender.append(any(), any(), any(), eq(EventType.MESSAGE), any(), any()))
                .thenReturn(new SessionEventAppender.AppendResult(true, messageEvent(1L)));

        CollectEventResult result = sessionEventService.collect(SESSION_ID, messageRequest());

        assertThat(result.created()).isTrue();
        assertThat(result.event().serverSequence()).isEqualTo(1L);
        assertThat(result.event().payload().get("content").asText()).isEqualTo("hi");
        verify(messageRepository).save(any(SessionMessage.class));
    }

    @Test
    @DisplayName("collect - 동일 eventId 재요청은 멱등(created=false, projection 저장 없음)")
    void collect_isIdempotent_onDuplicate() {
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(Session.start(OCCURRED)));
        when(participantRepository.findBySessionIdAndUserId(SESSION_ID, "alice")).thenReturn(Optional.of(participant()));
        when(eventAppender.append(any(), any(), any(), eq(EventType.MESSAGE), any(), any()))
                .thenReturn(new SessionEventAppender.AppendResult(false, messageEvent(5L)));

        CollectEventResult result = sessionEventService.collect(SESSION_ID, messageRequest());

        assertThat(result.created()).isFalse();
        assertThat(result.event().serverSequence()).isEqualTo(5L);
        verify(messageRepository, never()).save(any());
    }

    @Test
    @DisplayName("collect - MESSAGE가 아니면 UnsupportedEventTypeException")
    void collect_throwsUnsupportedType() {
        CollectEventRequest joinReq = new CollectEventRequest("e1", EventType.JOIN, "alice", "hi", OCCURRED);

        assertThatThrownBy(() -> sessionEventService.collect(SESSION_ID, joinReq))
                .isInstanceOf(UnsupportedEventTypeException.class);
    }

    @Test
    @DisplayName("collect - 세션 없음 시 SessionNotFoundException")
    void collect_throwsSessionNotFound() {
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionEventService.collect(SESSION_ID, messageRequest()))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    @DisplayName("collect - 비활성 세션이면 SessionNotActiveException")
    void collect_throwsSessionNotActive() {
        Session completed = Session.start(OCCURRED);
        completed.complete(OCCURRED);
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(completed));

        assertThatThrownBy(() -> sessionEventService.collect(SESSION_ID, messageRequest()))
                .isInstanceOf(SessionNotActiveException.class);
    }

    @Test
    @DisplayName("collect - sender가 참여자가 아니면 SenderNotParticipantException")
    void collect_throwsSenderNotParticipant() {
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(Session.start(OCCURRED)));
        when(participantRepository.findBySessionIdAndUserId(SESSION_ID, "alice")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionEventService.collect(SESSION_ID, messageRequest()))
                .isInstanceOf(SenderNotParticipantException.class);
    }
}
