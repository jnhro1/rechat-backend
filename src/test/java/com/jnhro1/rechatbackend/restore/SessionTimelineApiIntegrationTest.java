package com.jnhro1.rechatbackend.restore;
import com.jnhro1.rechatbackend.session.Session;
import com.jnhro1.rechatbackend.session.SessionRepository;
import com.jnhro1.rechatbackend.event.SessionEvent;
import com.jnhro1.rechatbackend.event.SessionEventRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jnhro1.rechatbackend.event.enums.EventType;
import com.jnhro1.rechatbackend.support.IntegrationTestBase;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
class SessionTimelineApiIntegrationTest extends IntegrationTestBase {

    private static final Instant T0 = Instant.parse("2026-06-28T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-06-28T00:00:01Z");
    private static final Instant T2 = Instant.parse("2026-06-28T00:00:02Z");
    private static final Instant T3 = Instant.parse("2026-06-28T00:00:03Z");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private SessionRepository sessionRepository;
    @Autowired
    private SessionEventRepository eventRepository;

    @AfterEach
    void tearDown() {
        eventRepository.deleteAllInBatch();
        sessionRepository.deleteAllInBatch();
    }

    private Long newSession() {
        return sessionRepository.save(Session.start(T0)).getId();
    }

    private void seedMessage(Long sessionId, long seq, String content, Instant occurredAt) {
        eventRepository.save(SessionEvent.of(sessionId, seq, "m" + seq, "alice",
                EventType.MESSAGE, "{\"content\":\"%s\"}".formatted(content), occurredAt));
    }

    private void seedParticipantEvent(Long sessionId, long seq, EventType type, String userId, Instant occurredAt) {
        eventRepository.save(SessionEvent.of(sessionId, seq, "p" + seq, userId, type, null, occurredAt));
    }

    /** JOIN alice(seq1,T0) + 메시지 3건(seq2~4, T1~T3). */
    private Long seedSessionWithThreeMessages() {
        Long sessionId = newSession();
        seedParticipantEvent(sessionId, 1, EventType.JOIN, "alice", T0);
        seedMessage(sessionId, 2, "first", T1);
        seedMessage(sessionId, 3, "second", T2);
        seedMessage(sessionId, 4, "third", T3);
        return sessionId;
    }

    private ResultActions timeline(Long sessionId, String at) throws Exception {
        var req = get("/api/v1/sessions/{id}/timeline", sessionId);
        if (at != null) {
            req = req.param("at", at);
        }
        return mockMvc.perform(req);
    }

    @Test
    @DisplayName("timeline - at 미지정이면 전체 메시지 + 참여자(이벤트 fold)를 복원한다")
    void timeline_full_whenNoAt() throws Exception {
        Long sessionId = seedSessionWithThreeMessages();

        timeline(sessionId, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages.length()").value(3))
                .andExpect(jsonPath("$.data.upToSequence").value(4))
                .andExpect(jsonPath("$.data.participants.length()").value(1))
                .andExpect(jsonPath("$.data.participants[0].userId").value("alice"))
                .andExpect(jsonPath("$.data.participants[0].presence").value("ONLINE"));
    }

    @Test
    @DisplayName("timeline - at 시점 이전 메시지만 복원한다(occurredAt <= at)")
    void timeline_pointInTime_filtersMessages() throws Exception {
        Long sessionId = seedSessionWithThreeMessages();

        timeline(sessionId, "2026-06-28T00:00:02Z")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages.length()").value(2))
                .andExpect(jsonPath("$.data.messages[0].content").value("first"))
                .andExpect(jsonPath("$.data.messages[1].content").value("second"))
                .andExpect(jsonPath("$.data.upToSequence").value(3));
    }

    @Test
    @DisplayName("timeline - messageLimit으로 최근 N개만 복원한다")
    void timeline_appliesMessageLimit() throws Exception {
        Long sessionId = seedSessionWithThreeMessages();

        mockMvc.perform(get("/api/v1/sessions/{id}/timeline", sessionId).param("messageLimit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages.length()").value(1))
                .andExpect(jsonPath("$.data.messages[0].content").value("third"));
    }

    @Test
    @DisplayName("timeline - 참여자는 JOIN 이벤트의 occurredAt <= at 시점만 복원한다")
    void timeline_participantPointInTime() throws Exception {
        Long sessionId = newSession();
        seedParticipantEvent(sessionId, 1, EventType.JOIN, "alice", T0);
        seedParticipantEvent(sessionId, 2, EventType.JOIN, "bob", T2);

        timeline(sessionId, "2026-06-28T00:00:01Z")   // T0 < T1 < T2 → alice만
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.participants.length()").value(1))
                .andExpect(jsonPath("$.data.participants[0].userId").value("alice"));
    }

    @Test
    @DisplayName("timeline - DISCONNECT가 시점 presence(OFFLINE)에 반영된다")
    void timeline_foldsPresence() throws Exception {
        Long sessionId = newSession();
        seedParticipantEvent(sessionId, 1, EventType.JOIN, "alice", T0);
        seedParticipantEvent(sessionId, 2, EventType.DISCONNECT, "alice", T1);

        timeline(sessionId, null)
                .andExpect(jsonPath("$.data.participants[0].presence").value("OFFLINE"));
    }

    @Test
    @DisplayName("timeline - 같은 요청을 두 번 복원해도 동일한 결과(결정성)")
    void timeline_isDeterministic() throws Exception {
        Long sessionId = seedSessionWithThreeMessages();

        JsonNode first = dataOf(timeline(sessionId, "2026-06-28T00:00:03Z"));
        JsonNode second = dataOf(timeline(sessionId, "2026-06-28T00:00:03Z"));

        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("timeline - 없는 세션이면 404 SESSION_NOT_FOUND")
    void timeline_sessionMissing() throws Exception {
        timeline(999_999L, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"));
    }

    private JsonNode dataOf(ResultActions actions) throws Exception {
        String body = actions.andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data");
    }
}
