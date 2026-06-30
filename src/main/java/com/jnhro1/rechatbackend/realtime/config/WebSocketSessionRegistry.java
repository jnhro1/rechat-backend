package com.jnhro1.rechatbackend.realtime.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class WebSocketSessionRegistry {

    public record SessionUser(Long sessionId, String userId) {
    }

    private final Map<String, SessionUser> byStompSession = new ConcurrentHashMap<>();

    public void bind(String stompSessionId, Long sessionId, String userId) {
        byStompSession.put(stompSessionId, new SessionUser(sessionId, userId));
    }

    public SessionUser get(String stompSessionId) {
        return byStompSession.get(stompSessionId);
    }

    public SessionUser remove(String stompSessionId) {
        return byStompSession.remove(stompSessionId);
    }
}
