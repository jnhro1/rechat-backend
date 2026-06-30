package com.jnhro1.rechatbackend.realtime;
import com.jnhro1.rechatbackend.realtime.request.WsSendRequest;
import com.jnhro1.rechatbackend.realtime.request.WsResumeRequest;

import com.jnhro1.rechatbackend.common.util.IdempotencyKeyResolver;
import com.jnhro1.rechatbackend.event.SessionEventService;
import com.jnhro1.rechatbackend.event.enums.EventType;
import com.jnhro1.rechatbackend.event.request.CollectEventRequest;
import com.jnhro1.rechatbackend.event.response.EventSliceResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class SessionMessageController {

    private final SessionEventService sessionEventService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/sessions/{sessionId}/send")
    public void send(@DestinationVariable Long sessionId, @Valid @Payload WsSendRequest request, Principal principal) {
        String senderId = requirePrincipal(principal);
        String eventId = IdempotencyKeyResolver.resolve(request.eventId());
        CollectEventRequest collectRequest = new CollectEventRequest(
                eventId, EventType.MESSAGE, senderId, request.content(), request.occurredAt());
        sessionEventService.collect(sessionId, collectRequest);
    }

    @MessageMapping("/sessions/{sessionId}/resume")
    public void resume(@DestinationVariable Long sessionId, @Valid @Payload WsResumeRequest request, Principal principal) {
        String userId = requirePrincipal(principal);
        EventSliceResponse slice = sessionEventService.getEventsAfter(sessionId, request.afterSequence(), request.limitOrDefault());
        slice.events().forEach(event ->
                messagingTemplate.convertAndSendToUser(userId, "/queue/sessions/" + sessionId, event));
    }

    @MessageExceptionHandler
    public void handleException(Exception e, Principal principal) {
        log.warn("WebSocket 메시지 처리 실패: {}", e.getMessage());
        if (principal != null) {
            messagingTemplate.convertAndSendToUser(principal.getName(), "/queue/errors", e.getMessage());
        }
    }

    private String requirePrincipal(Principal principal) {
        if (principal == null) {
            throw new IllegalStateException("CONNECT 헤더의 userId가 없습니다.");
        }
        return principal.getName();
    }
}
