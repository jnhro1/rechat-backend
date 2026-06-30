package com.jnhro1.rechatbackend.realtime.config;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class StompPrincipalChannelInterceptor implements ChannelInterceptor {

    private final WebSocketSessionRegistry sessionRegistry;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }
        String userId = accessor.getFirstNativeHeader("userId");
        String sessionId = accessor.getFirstNativeHeader("sessionId");
        if (StringUtils.hasText(userId)) {
            accessor.setUser(new StompPrincipal(userId));
            String stompSessionId = accessor.getSessionId();
            if (stompSessionId != null && StringUtils.hasText(sessionId)) {
                sessionRegistry.bind(stompSessionId, Long.valueOf(sessionId), userId);
            }
        }
        return message;
    }
}
