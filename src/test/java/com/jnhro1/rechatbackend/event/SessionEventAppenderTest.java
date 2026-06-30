package com.jnhro1.rechatbackend.event;
import com.jnhro1.rechatbackend.session.Session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jnhro1.rechatbackend.event.SessionEventAppender.AppendResult;
import com.jnhro1.rechatbackend.event.enums.EventType;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionEventAppenderTest {

    private static final Instant NOW = Instant.parse("2026-06-28T00:00:00Z");

    @Mock
    private SessionEventRepository eventRepository;
    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private SessionEventAppender eventAppender;

    @Test
    @DisplayName("append - 신규면 server_sequence 채번 후 저장(created=true)")
    void append_allocatesSequence_whenNew() {
        Session session = Session.start(NOW); // lastSequence 0
        when(eventRepository.findBySessionIdAndEventId(any(), eq("e1"))).thenReturn(Optional.empty());
        when(eventRepository.save(any(SessionEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        AppendResult result = eventAppender.append(session, "e1", "alice", EventType.JOIN, null, NOW);

        assertThat(result.created()).isTrue();
        assertThat(result.event().getServerSequence()).isEqualTo(1L);
        assertThat(session.getLastSequence()).isEqualTo(1L);
    }

    @Test
    @DisplayName("append - 동일 eventId가 있으면 기존 반환(created=false, 채번·저장 없음)")
    void append_returnsExisting_whenDuplicate() {
        Session session = Session.start(NOW);
        SessionEvent existing = SessionEvent.of(1L, 7L, "e1", "alice", EventType.JOIN, null, NOW);
        when(eventRepository.findBySessionIdAndEventId(any(), eq("e1"))).thenReturn(Optional.of(existing));

        AppendResult result = eventAppender.append(session, "e1", "alice", EventType.JOIN, null, NOW);

        assertThat(result.created()).isFalse();
        assertThat(result.event().getServerSequence()).isEqualTo(7L);
        assertThat(session.getLastSequence()).isZero();
        verify(eventRepository, never()).save(any());
    }
}
