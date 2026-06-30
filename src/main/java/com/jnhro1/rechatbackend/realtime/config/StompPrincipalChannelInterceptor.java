package com.jnhro1.rechatbackend.realtime.config;

import com.jnhro1.rechatbackend.participant.SessionParticipantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class StompPrincipalChannelInterceptor implements ChannelInterceptor {

    private static final String SESSION_TOPIC_PREFIX = "/topic/sessions/";

    private final WebSocketSessionRegistry sessionRegistry;
    private final SessionParticipantRepository participantRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        if (StompCommand.CONNECT.equals(command)) {
            bindPrincipal(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(command)) {
            authorizeSubscribe(accessor);
        }
        return message;
    }

    private void bindPrincipal(StompHeaderAccessor accessor) {
        String userId = accessor.getFirstNativeHeader("userId");
        String sessionId = accessor.getFirstNativeHeader("sessionId");
        if (StringUtils.hasText(userId)) {
            accessor.setUser(new StompPrincipal(userId));
            String stompSessionId = accessor.getSessionId();
            if (stompSessionId != null && StringUtils.hasText(sessionId)) {
                sessionRegistry.bind(stompSessionId, Long.valueOf(sessionId), userId);
            }
        }
    }

    // 세션 토픽(/topic/sessions/{id}) 구독은 인증·인가가 없으므로 읽기 권한을 여기서 막는다.
    // 해당 세션의 활성 참여자가 아니면 구독을 거부해 비참여자가 대화를 엿보지 못하게 한다.
    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(SESSION_TOPIC_PREFIX)) {
            return;
        }
        Long sessionId = parseSessionId(destination);
        String userId = (accessor.getUser() != null) ? accessor.getUser().getName() : null;
        if (sessionId == null || userId == null || !isActiveParticipant(sessionId, userId)) {
            throw new MessagingException(
                    "세션 참여자만 구독할 수 있습니다: session=" + sessionId + ", user=" + userId);
        }
    }

    private Long parseSessionId(String destination) {
        try {
            return Long.valueOf(destination.substring(SESSION_TOPIC_PREFIX.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isActiveParticipant(Long sessionId, String userId) {
        return participantRepository.findBySessionIdAndUserId(sessionId, userId)
                .filter(participant -> participant.getLeftAt() == null)
                .isPresent();
    }
}
