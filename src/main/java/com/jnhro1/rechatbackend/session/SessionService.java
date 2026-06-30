package com.jnhro1.rechatbackend.session;
import com.jnhro1.rechatbackend.participant.SessionParticipantRepository;

import com.jnhro1.rechatbackend.common.response.PageResponse;
import com.jnhro1.rechatbackend.session.enums.SessionSortType;
import com.jnhro1.rechatbackend.session.enums.SessionStatus;
import com.jnhro1.rechatbackend.session.exception.SessionNotFoundException;
import com.jnhro1.rechatbackend.session.request.SessionSearchCondition;
import com.jnhro1.rechatbackend.session.response.SessionResponse;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository;
    private final SessionParticipantRepository participantRepository;
    private final Clock clock;

    @Transactional
    public SessionResponse createSession() {
        Instant now = Instant.now(clock);
        Session session = Session.start(now);
        Session saved = sessionRepository.save(session);
        return SessionResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public SessionResponse getById(Long id) {
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new SessionNotFoundException(id));
        return SessionResponse.from(session);
    }

    /**
     * 세션을 종료(COMPLETED)하고 남은 참여자를 전원 OFFLINE으로 전이한다. 멱등 — 이미 종료됐으면 현재 상태를 반환한다.
     * 세션 행 비관적 락으로 join/이벤트 수집과 직렬화한다.
     */
    @Transactional
    public SessionResponse end(Long id) {
        Session session = sessionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new SessionNotFoundException(id));
        if (session.getStatus() == SessionStatus.COMPLETED) {
            return SessionResponse.from(session);
        }

        Instant now = Instant.now(clock);
        session.complete(now);
        // 세션 종료로 남은 참여자 연결 해제(전원 OFFLINE)
        participantRepository.findBySessionId(id).forEach(participant -> participant.disconnect(now));
        return SessionResponse.from(session);
    }

    @Transactional(readOnly = true)
    public PageResponse<SessionResponse> search(SessionSearchCondition cond, int page, int size, SessionSortType sort) {
        PageRequest pageRequest = PageRequest.of(page, size, sort.getSort());
        Page<SessionResponse> result = sessionRepository
                .search(cond.status(), cond.from(), cond.to(), cond.participantId(), pageRequest)
                .map(SessionResponse::from);
        return PageResponse.from(result);
    }
}
