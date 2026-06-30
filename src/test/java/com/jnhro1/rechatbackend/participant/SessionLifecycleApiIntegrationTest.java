package com.jnhro1.rechatbackend.participant;
import com.jnhro1.rechatbackend.session.Session;
import com.jnhro1.rechatbackend.session.SessionRepository;
import com.jnhro1.rechatbackend.event.SessionEventRepository;
import com.jnhro1.rechatbackend.event.message.SessionMessageRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jnhro1.rechatbackend.participant.enums.PresenceStatus;
import com.jnhro1.rechatbackend.session.enums.SessionStatus;
import com.jnhro1.rechatbackend.support.IntegrationTestBase;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
class SessionLifecycleApiIntegrationTest extends IntegrationTestBase {

    private static final Instant NOW = Instant.parse("2026-06-28T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SessionRepository sessionRepository;
    @Autowired
    private SessionParticipantRepository participantRepository;
    @Autowired
    private SessionEventRepository eventRepository;
    @Autowired
    private SessionMessageRepository messageRepository;

    @AfterEach
    void tearDown() {
        messageRepository.deleteAllInBatch();
        eventRepository.deleteAllInBatch();
        participantRepository.deleteAllInBatch();
        sessionRepository.deleteAllInBatch();
    }

    private ResultActions join(Long sessionId, String userId) throws Exception {
        return mockMvc.perform(post("/api/v1/sessions/{id}/join", sessionId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"%s\"}".formatted(userId)));
    }

    private ResultActions leave(Long sessionId, String userId) throws Exception {
        return mockMvc.perform(post("/api/v1/sessions/{id}/leave", sessionId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"%s\"}".formatted(userId)));
    }

    private ResultActions end(Long sessionId) throws Exception {
        return mockMvc.perform(post("/api/v1/sessions/{id}/end", sessionId));
    }

    @Test
    @DisplayName("leave - 참여 후 퇴장하면 200 OFFLINE + leftAt, 재퇴장도 멱등")
    void leave_setsOffline_andIdempotent() throws Exception {
        Long sessionId = sessionRepository.save(Session.start(NOW)).getId();
        join(sessionId, "alice").andExpect(status().isOk());

        leave(sessionId, "alice")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.presence").value("OFFLINE"))
                .andExpect(jsonPath("$.data.leftAt").exists());
        leave(sessionId, "alice").andExpect(status().isOk());

        assertThat(participantRepository.findBySessionIdAndUserId(sessionId, "alice"))
                .get().extracting(SessionParticipant::getPresence).isEqualTo(PresenceStatus.OFFLINE);
    }

    @Test
    @DisplayName("leave - 참여자가 아니면 404 PARTICIPANT_NOT_FOUND")
    void leave_notParticipant() throws Exception {
        Long sessionId = sessionRepository.save(Session.start(NOW)).getId();

        leave(sessionId, "alice")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PARTICIPANT_NOT_FOUND"));
    }

    @Test
    @DisplayName("leave - 없는 세션이면 404 SESSION_NOT_FOUND")
    void leave_sessionMissing() throws Exception {
        leave(999_999L, "alice")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("end - 세션 COMPLETED + 참여자 전원 OFFLINE, 재종료도 멱등")
    void end_completesAndOfflinesParticipants() throws Exception {
        Long sessionId = sessionRepository.save(Session.start(NOW)).getId();
        join(sessionId, "alice").andExpect(status().isOk());
        join(sessionId, "bob").andExpect(status().isOk());

        end(sessionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.endedAt").exists());
        end(sessionId).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"));

        assertThat(sessionRepository.findById(sessionId)).get()
                .extracting(Session::getStatus).isEqualTo(SessionStatus.COMPLETED);
        assertThat(participantRepository.findBySessionId(sessionId))
                .allMatch(p -> p.getPresence() == PresenceStatus.OFFLINE);
    }

    @Test
    @DisplayName("end - 종료된 세션엔 이벤트 수집이 막힌다(409 SESSION_NOT_ACTIVE)")
    void end_blocksFurtherEvents() throws Exception {
        Long sessionId = sessionRepository.save(Session.start(NOW)).getId();
        join(sessionId, "alice").andExpect(status().isOk());
        end(sessionId).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/sessions/{id}/events", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventId":"e1","type":"MESSAGE","senderId":"alice","content":"hi","occurredAt":"2026-06-28T00:00:01Z"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_ACTIVE"));
    }

    @Test
    @DisplayName("end - 없는 세션이면 404 SESSION_NOT_FOUND")
    void end_sessionMissing() throws Exception {
        end(999_999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"));
    }
}
