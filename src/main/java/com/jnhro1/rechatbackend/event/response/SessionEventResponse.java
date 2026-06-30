package com.jnhro1.rechatbackend.event.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jnhro1.rechatbackend.event.SessionEvent;
import com.jnhro1.rechatbackend.event.enums.EventType;
import java.time.Instant;

public record SessionEventResponse(
        Long id,
        Long sessionId,
        long serverSequence,
        String eventId,
        String senderId,
        EventType type,
        JsonNode payload,
        Instant occurredAt,
        Instant receivedAt
) {

    public static SessionEventResponse from(SessionEvent event, ObjectMapper objectMapper) {
        return new SessionEventResponse(
                event.getId(),
                event.getSessionId(),
                event.getServerSequence(),
                event.getEventId(),
                event.getSenderId(),
                event.getType(),
                parsePayload(event.getPayload(), objectMapper),
                event.getOccurredAt(),
                event.getCreatedAt()
        );
    }

    private static JsonNode parsePayload(String payload, ObjectMapper objectMapper) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException e) {
            // FYI: payload는 서버가 직렬화해 저장한 유효 JSON이라 실질적으로 도달하지 않는 경로.
            throw new IllegalStateException("이벤트 payload JSON 파싱 실패: eventId 기준 데이터 손상 의심", e);
        }
    }
}
