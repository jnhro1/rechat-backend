package com.jnhro1.rechatbackend.participant;
import com.jnhro1.rechatbackend.session.Session;
import com.jnhro1.rechatbackend.session.SessionRepository;
import com.jnhro1.rechatbackend.event.SessionEventAppender;

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
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SessionParticipantService {

    private final SessionRepository sessionRepository;
    private final SessionParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final SessionEventAppender eventAppender;
    private final Clock clock;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SessionParticipantResponse join(Long sessionId, String userId, String eventId) {
        if (!userRepository.existsByUsername(userId)) {
            throw new UserNotFoundException(userId);
        }
        Session session = lockActiveSession(sessionId);

        Optional<SessionParticipant> existing = participantRepository.findBySessionIdAndUserId(sessionId, userId);
        if (existing.isPresent() && existing.get().getLeftAt() == null) {
            return SessionParticipantResponse.from(existing.get());
        }
        if (participantRepository.countBySessionIdAndLeftAtIsNull(sessionId) >= Session.MAX_PARTICIPANTS) {
            throw new SessionFullException(sessionId);
        }

        Instant now = Instant.now(clock);
        eventAppender.append(session, eventId, userId, EventType.JOIN, null, now);
        SessionParticipant participant = existing
                .map(left -> { left.rejoin(now); return left; })
                .orElseGet(() -> participantRepository.save(SessionParticipant.join(sessionId, userId, now)));
        return SessionParticipantResponse.from(participant);
    }

    @Transactional
    public SessionParticipantResponse leave(Long sessionId, String userId, String eventId) {
        Session session = lockSession(sessionId);
        SessionParticipant participant = participantRepository.findBySessionIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ParticipantNotFoundException(sessionId, userId));
        if (participant.getLeftAt() != null) {
            return SessionParticipantResponse.from(participant);
        }
        appendAndApply(session, participant, eventId, userId, EventType.LEAVE, SessionParticipant::leave);
        return SessionParticipantResponse.from(participant);
    }

    @Transactional
    public SessionParticipantResponse disconnect(Long sessionId, String userId, String eventId) {
        Session session = lockSession(sessionId);
        SessionParticipant participant = requireActiveParticipant(sessionId, userId);
        if (participant.getPresence() == PresenceStatus.OFFLINE) {
            return SessionParticipantResponse.from(participant);
        }
        appendAndApply(session, participant, eventId, userId, EventType.DISCONNECT, SessionParticipant::disconnect);
        return SessionParticipantResponse.from(participant);
    }

    @Transactional
    public SessionParticipantResponse reconnect(Long sessionId, String userId, String eventId) {
        Session session = lockSession(sessionId);
        SessionParticipant participant = requireActiveParticipant(sessionId, userId);
        if (participant.getPresence() == PresenceStatus.ONLINE) {
            return SessionParticipantResponse.from(participant);
        }
        appendAndApply(session, participant, eventId, userId, EventType.RECONNECT, SessionParticipant::reconnect);
        return SessionParticipantResponse.from(participant);
    }

    private void appendAndApply(Session session, SessionParticipant participant, String eventId, String userId,
                                EventType type, ParticipantTransition transition) {
        Instant now = Instant.now(clock);
        eventAppender.append(session, eventId, userId, type, null, now);
        transition.apply(participant, now);
    }

    private Session lockActiveSession(Long sessionId) {
        Session session = lockSession(sessionId);
        if (!session.isActive()) {
            throw new SessionNotActiveException(sessionId);
        }
        return session;
    }

    private Session lockSession(Long sessionId) {
        return sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));
    }

    private SessionParticipant requireActiveParticipant(Long sessionId, String userId) {
        SessionParticipant participant = activeParticipantOrNull(sessionId, userId);
        if (participant == null) {
            throw new ParticipantNotFoundException(sessionId, userId);
        }
        return participant;
    }

    private SessionParticipant activeParticipantOrNull(Long sessionId, String userId) {
        return participantRepository.findBySessionIdAndUserId(sessionId, userId)
                .filter(p -> p.getLeftAt() == null)
                .orElse(null);
    }

    @FunctionalInterface
    private interface ParticipantTransition {
        void apply(SessionParticipant participant, Instant now);
    }
}
