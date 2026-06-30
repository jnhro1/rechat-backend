package com.jnhro1.rechatbackend.restore;
import com.jnhro1.rechatbackend.event.SessionEvent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jnhro1.rechatbackend.participant.enums.PresenceStatus;
import com.jnhro1.rechatbackend.event.payload.MessagePayload;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SessionStateReducer {

    private final ObjectMapper objectMapper;

    public SessionState reduce(SessionState base, List<SessionEvent> events) {
        SessionState state = (base != null) ? base : new SessionState();
        for (SessionEvent event : events) {
            apply(state, event);
            state.advanceTo(event.getServerSequence());
        }
        return state;
    }

    private void apply(SessionState state, SessionEvent event) {
        switch (event.getType()) {
            case MESSAGE -> state.addMessage(
                    event.getServerSequence(), event.getSenderId(), parseContent(event.getPayload()), event.getOccurredAt());
            case JOIN -> state.join(event.getSenderId(), event.getOccurredAt());
            case LEAVE -> state.transition(event.getSenderId(), PresenceStatus.OFFLINE, true);
            case DISCONNECT -> state.transition(event.getSenderId(), PresenceStatus.OFFLINE, false);
            case RECONNECT -> state.transition(event.getSenderId(), PresenceStatus.ONLINE, false);
            default -> { /* EDIT/DELETE 등은 향후 확장 */ }
        }
    }

    private String parseContent(String payload) {
        try {
            return objectMapper.readValue(payload, MessagePayload.class).content();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("이벤트 payload 파싱 실패: 데이터 손상 의심", e);
        }
    }
}
