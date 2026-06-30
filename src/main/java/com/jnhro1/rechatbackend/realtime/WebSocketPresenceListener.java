package com.jnhro1.rechatbackend.realtime;

import com.jnhro1.rechatbackend.common.exception.BusinessException;
import com.jnhro1.rechatbackend.common.util.IdempotencyKeyResolver;
import com.jnhro1.rechatbackend.realtime.config.WebSocketSessionRegistry;
import com.jnhro1.rechatbackend.realtime.config.WebSocketSessionRegistry.SessionUser;
import com.jnhro1.rechatbackend.participant.SessionParticipantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketPresenceListener {

    private final SessionParticipantService sessionParticipantService;
    private final WebSocketSessionRegistry sessionRegistry;

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        String stompSessionId = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();
        applyPresence(sessionRegistry.get(stompSessionId), true);
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        applyPresence(sessionRegistry.remove(event.getSessionId()), false);
    }

    private void applyPresence(SessionUser sessionUser, boolean connected) {
        if (sessionUser == null) {
            return;
        }
        String eventId = IdempotencyKeyResolver.resolve(null);
        try {
            if (connected) {
                sessionParticipantService.reconnect(sessionUser.sessionId(), sessionUser.userId(), eventId);
            } else {
                sessionParticipantService.disconnect(sessionUser.sessionId(), sessionUser.userId(), eventId);
            }
        } catch (BusinessException ignored) {
            // 세션 없음/참여자 아님 등 → presence 동기화는 조용히 skip (연결 자체는 정상)
            log.debug("presence sync skip: session={}, user={}, reason={}",
                    sessionUser.sessionId(), sessionUser.userId(), ignored.getErrorCode().getCode());
        }
    }
}
