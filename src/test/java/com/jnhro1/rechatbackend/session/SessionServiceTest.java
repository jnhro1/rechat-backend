package com.jnhro1.rechatbackend.session;
import com.jnhro1.rechatbackend.participant.SessionParticipant;
import com.jnhro1.rechatbackend.participant.SessionParticipantRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.mockito.Mockito.never;

import com.jnhro1.rechatbackend.common.response.PageResponse;
import com.jnhro1.rechatbackend.participant.enums.PresenceStatus;
import com.jnhro1.rechatbackend.session.enums.SessionSortType;
import com.jnhro1.rechatbackend.session.enums.SessionStatus;
import com.jnhro1.rechatbackend.session.exception.SessionNotFoundException;
import com.jnhro1.rechatbackend.session.request.SessionSearchCondition;
import com.jnhro1.rechatbackend.session.response.SessionResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-06-28T00:00:00Z");

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private SessionParticipantRepository participantRepository;

    // 고정 시계 — startedAt이 결정적으로 FIXED_NOW가 되는지 검증
    @org.mockito.Spy
    private Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    @InjectMocks
    private SessionService sessionService;

    @Test
    @DisplayName("createSession - ACTIVE 상태 + 고정 시각으로 세션을 생성하고 저장한다")
    void createSession_returnsActiveSession() {
        when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> inv.getArgument(0));

        SessionResponse response = sessionService.createSession();

        assertThat(response.status()).isEqualTo(SessionStatus.ACTIVE);
        assertThat(response.startedAt()).isEqualTo(FIXED_NOW);
        verify(sessionRepository, times(1)).save(any(Session.class));
    }

    @Test
    @DisplayName("getById - 존재하는 세션을 응답으로 매핑한다")
    void getById_returnsSession() {
        Session session = Session.start(FIXED_NOW);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        SessionResponse response = sessionService.getById(1L);

        assertThat(response.status()).isEqualTo(SessionStatus.ACTIVE);
        assertThat(response.startedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    @DisplayName("getById - 세션 없음 시 SessionNotFoundException")
    void getById_throwsWhenMissing() {
        when(sessionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.getById(99L))
                .isInstanceOf(SessionNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("search - 리포지토리 Page를 PageResponse로 매핑한다(메타 포함)")
    void search_returnsPageResponse() {
        Session s1 = Session.start(FIXED_NOW);
        Session s2 = Session.start(FIXED_NOW);
        Page<Session> page = new PageImpl<>(List.of(s1, s2), PageRequest.of(0, 2), 3);
        when(sessionRepository.search(any(), any(), any(), any(), any(Pageable.class))).thenReturn(page);

        PageResponse<SessionResponse> result = sessionService.search(
                SessionSearchCondition.of(null, null, null, null), 0, 2, SessionSortType.CREATED_DESC);

        assertThat(result.content()).hasSize(2);
        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.hasNext()).isTrue();
    }

    @Test
    @DisplayName("end - 세션을 COMPLETED로 전이하고 참여자를 전원 OFFLINE으로 만든다")
    void end_completesAndMarksParticipantsOffline() {
        Session session = Session.start(FIXED_NOW);
        SessionParticipant alice = SessionParticipant.join(1L, "alice", FIXED_NOW);
        SessionParticipant bob = SessionParticipant.join(1L, "bob", FIXED_NOW);
        when(sessionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionId(1L)).thenReturn(List.of(alice, bob));

        SessionResponse response = sessionService.end(1L);

        assertThat(response.status()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(response.endedAt()).isEqualTo(FIXED_NOW);
        assertThat(alice.getPresence()).isEqualTo(PresenceStatus.OFFLINE);
        assertThat(bob.getPresence()).isEqualTo(PresenceStatus.OFFLINE);
    }

    @Test
    @DisplayName("end - 이미 COMPLETED면 멱등(참여자 미조회·미변경)")
    void end_isIdempotent_whenAlreadyCompleted() {
        Session session = Session.start(FIXED_NOW);
        session.complete(FIXED_NOW);
        when(sessionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(session));

        SessionResponse response = sessionService.end(1L);

        assertThat(response.status()).isEqualTo(SessionStatus.COMPLETED);
        verify(participantRepository, never()).findBySessionId(any());
    }

    @Test
    @DisplayName("end - 세션 없음 시 SessionNotFoundException")
    void end_throwsWhenMissing() {
        when(sessionRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.end(99L))
                .isInstanceOf(SessionNotFoundException.class);
    }
}
