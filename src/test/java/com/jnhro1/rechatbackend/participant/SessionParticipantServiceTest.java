package com.jnhro1.rechatbackend.participant;
import com.jnhro1.rechatbackend.session.Session;
import com.jnhro1.rechatbackend.session.SessionRepository;
import com.jnhro1.rechatbackend.event.SessionEventAppender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jnhro1.rechatbackend.event.enums.EventType;
import com.jnhro1.rechatbackend.participant.enums.PresenceStatus;
import com.jnhro1.rechatbackend.participant.exception.ParticipantNotFoundException;
import com.jnhro1.rechatbackend.participant.exception.SessionFullException;
import com.jnhro1.rechatbackend.session.exception.SessionNotActiveException;
import com.jnhro1.rechatbackend.session.exception.SessionNotFoundException;
import com.jnhro1.rechatbackend.participant.response.SessionParticipantResponse;
import com.jnhro1.rechatbackend.user.UserRepository;
import com.jnhro1.rechatbackend.user.exception.UserNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionParticipantServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-06-28T00:00:00Z");
    private static final Long SESSION_ID = 1L;
    private static final String USER_ID = "alice";
    private static final String EVENT_ID = "evt-1";

    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private SessionParticipantRepository participantRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SessionEventAppender eventAppender;
    @Spy
    private Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    @InjectMocks
    private SessionParticipantService sessionParticipantService;

    private void appendCalled() {
        verify(eventAppender).append(any(), any(), any(), any(), any(), any());
    }

    private void appendNotCalled() {
        verify(eventAppender, never()).append(any(), any(), any(), any(), any(), any());
    }

    // ---------- join ----------

    @Test
    @DisplayName("join - 유효하면 JOIN 이벤트 append + 참여자 ONLINE 생성")
    void join_createsParticipant_andAppendsEvent() {
        when(userRepository.existsByUsername(USER_ID)).thenReturn(true);
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(Session.start(FIXED_NOW)));
        when(participantRepository.findBySessionIdAndUserId(SESSION_ID, USER_ID)).thenReturn(Optional.empty());
        when(participantRepository.countBySessionIdAndLeftAtIsNull(SESSION_ID)).thenReturn(0L);
        when(participantRepository.save(any(SessionParticipant.class))).thenAnswer(inv -> inv.getArgument(0));

        SessionParticipantResponse response = sessionParticipantService.join(SESSION_ID, USER_ID, EVENT_ID);

        assertThat(response.presence()).isEqualTo(PresenceStatus.ONLINE);
        appendCalled();
        verify(participantRepository).save(any(SessionParticipant.class));
    }

    @Test
    @DisplayName("join - 떠났던 유저는 재입장(rejoin: ONLINE, leftAt 해제) + 이벤트")
    void join_rejoins_whenPreviouslyLeft() {
        SessionParticipant left = SessionParticipant.join(SESSION_ID, USER_ID, FIXED_NOW.minusSeconds(100));
        left.leave(FIXED_NOW.minusSeconds(50));
        when(userRepository.existsByUsername(USER_ID)).thenReturn(true);
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(Session.start(FIXED_NOW)));
        when(participantRepository.findBySessionIdAndUserId(SESSION_ID, USER_ID)).thenReturn(Optional.of(left));
        when(participantRepository.countBySessionIdAndLeftAtIsNull(SESSION_ID)).thenReturn(0L);

        SessionParticipantResponse response = sessionParticipantService.join(SESSION_ID, USER_ID, EVENT_ID);

        assertThat(response.presence()).isEqualTo(PresenceStatus.ONLINE);
        assertThat(response.leftAt()).isNull();
        appendCalled();
        verify(participantRepository, never()).save(any());
    }

    @Test
    @DisplayName("join - 이미 참여 중이면 멱등(이벤트·저장 없음)")
    void join_returnsExisting_whenAlreadyJoined() {
        SessionParticipant present = SessionParticipant.join(SESSION_ID, USER_ID, FIXED_NOW);
        when(userRepository.existsByUsername(USER_ID)).thenReturn(true);
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(Session.start(FIXED_NOW)));
        when(participantRepository.findBySessionIdAndUserId(SESSION_ID, USER_ID)).thenReturn(Optional.of(present));

        sessionParticipantService.join(SESSION_ID, USER_ID, EVENT_ID);

        appendNotCalled();
        verify(participantRepository, never()).save(any());
    }

    @Test
    @DisplayName("join - 정원(2명)이 차면 SessionFullException(이벤트 없음)")
    void join_throwsSessionFull() {
        when(userRepository.existsByUsername(USER_ID)).thenReturn(true);
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(Session.start(FIXED_NOW)));
        when(participantRepository.findBySessionIdAndUserId(SESSION_ID, USER_ID)).thenReturn(Optional.empty());
        when(participantRepository.countBySessionIdAndLeftAtIsNull(SESSION_ID)).thenReturn((long) Session.MAX_PARTICIPANTS);

        assertThatThrownBy(() -> sessionParticipantService.join(SESSION_ID, USER_ID, EVENT_ID))
                .isInstanceOf(SessionFullException.class);
        appendNotCalled();
    }

    @Test
    @DisplayName("join - 유저 없음 시 UserNotFoundException")
    void join_throwsUserNotFound() {
        when(userRepository.existsByUsername("ghost")).thenReturn(false);

        assertThatThrownBy(() -> sessionParticipantService.join(SESSION_ID, "ghost", EVENT_ID))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("join - 비활성 세션이면 SessionNotActiveException")
    void join_throwsSessionNotActive() {
        Session completed = Session.start(FIXED_NOW);
        completed.complete(FIXED_NOW);
        when(userRepository.existsByUsername(USER_ID)).thenReturn(true);
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(completed));

        assertThatThrownBy(() -> sessionParticipantService.join(SESSION_ID, USER_ID, EVENT_ID))
                .isInstanceOf(SessionNotActiveException.class);
    }

    // ---------- leave ----------

    @Test
    @DisplayName("leave - 참여 중이면 LEAVE 이벤트 + OFFLINE/leftAt")
    void leave_setsOffline_andAppends() {
        SessionParticipant present = SessionParticipant.join(SESSION_ID, USER_ID, FIXED_NOW);
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(Session.start(FIXED_NOW)));
        when(participantRepository.findBySessionIdAndUserId(SESSION_ID, USER_ID)).thenReturn(Optional.of(present));

        SessionParticipantResponse response = sessionParticipantService.leave(SESSION_ID, USER_ID, EVENT_ID);

        assertThat(response.presence()).isEqualTo(PresenceStatus.OFFLINE);
        assertThat(response.leftAt()).isEqualTo(FIXED_NOW);
        appendCalled();
    }

    @Test
    @DisplayName("leave - 이미 떠났으면 멱등(이벤트 없음)")
    void leave_isIdempotent_whenAlreadyLeft() {
        SessionParticipant left = SessionParticipant.join(SESSION_ID, USER_ID, FIXED_NOW);
        left.leave(FIXED_NOW);
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(Session.start(FIXED_NOW)));
        when(participantRepository.findBySessionIdAndUserId(SESSION_ID, USER_ID)).thenReturn(Optional.of(left));

        sessionParticipantService.leave(SESSION_ID, USER_ID, EVENT_ID);

        appendNotCalled();
    }

    @Test
    @DisplayName("leave - 참여자가 아니면 ParticipantNotFoundException")
    void leave_throwsParticipantNotFound() {
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(Session.start(FIXED_NOW)));
        when(participantRepository.findBySessionIdAndUserId(SESSION_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionParticipantService.leave(SESSION_ID, USER_ID, EVENT_ID))
                .isInstanceOf(ParticipantNotFoundException.class);
    }

    @Test
    @DisplayName("leave - 세션 없음 시 SessionNotFoundException")
    void leave_throwsSessionNotFound() {
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionParticipantService.leave(SESSION_ID, USER_ID, EVENT_ID))
                .isInstanceOf(SessionNotFoundException.class);
    }

    // ---------- disconnect / reconnect ----------

    @Test
    @DisplayName("disconnect - 온라인 참여자를 OFFLINE으로 + DISCONNECT 이벤트")
    void disconnect_setsOffline_andAppends() {
        SessionParticipant online = SessionParticipant.join(SESSION_ID, USER_ID, FIXED_NOW);
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(Session.start(FIXED_NOW)));
        when(participantRepository.findBySessionIdAndUserId(SESSION_ID, USER_ID)).thenReturn(Optional.of(online));

        SessionParticipantResponse response = sessionParticipantService.disconnect(SESSION_ID, USER_ID, EVENT_ID);

        assertThat(response.presence()).isEqualTo(PresenceStatus.OFFLINE);
        appendCalled();
    }

    @Test
    @DisplayName("disconnect - 이미 OFFLINE이면 멱등(이벤트 없음)")
    void disconnect_isIdempotent_whenAlreadyOffline() {
        SessionParticipant offline = SessionParticipant.join(SESSION_ID, USER_ID, FIXED_NOW);
        offline.disconnect(FIXED_NOW);
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(Session.start(FIXED_NOW)));
        when(participantRepository.findBySessionIdAndUserId(SESSION_ID, USER_ID)).thenReturn(Optional.of(offline));

        sessionParticipantService.disconnect(SESSION_ID, USER_ID, EVENT_ID);

        appendNotCalled();
    }

    @Test
    @DisplayName("reconnect - OFFLINE 참여자를 ONLINE으로 + RECONNECT 이벤트")
    void reconnect_setsOnline_andAppends() {
        SessionParticipant offline = SessionParticipant.join(SESSION_ID, USER_ID, FIXED_NOW);
        offline.disconnect(FIXED_NOW);
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(Session.start(FIXED_NOW)));
        when(participantRepository.findBySessionIdAndUserId(SESSION_ID, USER_ID)).thenReturn(Optional.of(offline));

        SessionParticipantResponse response = sessionParticipantService.reconnect(SESSION_ID, USER_ID, EVENT_ID);

        assertThat(response.presence()).isEqualTo(PresenceStatus.ONLINE);
        appendCalled();
    }

    @Test
    @DisplayName("disconnect - 떠난(leftAt) 참여자는 ParticipantNotFoundException")
    void disconnect_throwsWhenLeft() {
        SessionParticipant left = SessionParticipant.join(SESSION_ID, USER_ID, FIXED_NOW);
        left.leave(FIXED_NOW);
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(Session.start(FIXED_NOW)));
        when(participantRepository.findBySessionIdAndUserId(SESSION_ID, USER_ID)).thenReturn(Optional.of(left));

        assertThatThrownBy(() -> sessionParticipantService.disconnect(SESSION_ID, USER_ID, EVENT_ID))
                .isInstanceOf(ParticipantNotFoundException.class);
        appendNotCalled();
    }
}
