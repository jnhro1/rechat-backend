package com.jnhro1.rechatbackend.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jnhro1.rechatbackend.event.SessionEvent;
import com.jnhro1.rechatbackend.event.SessionEventAppended;
import com.jnhro1.rechatbackend.event.response.SessionEventResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class SessionEventBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEventAppended(SessionEventAppended appended) {
        SessionEvent event = appended.event();
        messagingTemplate.convertAndSend(
                "/topic/sessions/" + event.getSessionId(),
                SessionEventResponse.from(event, objectMapper));
    }
}
