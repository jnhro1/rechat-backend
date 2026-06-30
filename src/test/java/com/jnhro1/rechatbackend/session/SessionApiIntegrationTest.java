package com.jnhro1.rechatbackend.session;
import com.jnhro1.rechatbackend.participant.SessionParticipant;
import com.jnhro1.rechatbackend.participant.SessionParticipantRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jnhro1.rechatbackend.session.enums.SessionStatus;
import com.jnhro1.rechatbackend.support.IntegrationTestBase;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class SessionApiIntegrationTest extends IntegrationTestBase {

    private static final Instant NOW = Instant.parse("2026-06-28T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SessionParticipantRepository participantRepository;

    @AfterEach
    void tearDown() {
        participantRepository.deleteAllInBatch();
        sessionRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("POST /api/v1/sessions - 201과 ACTIVE 세션 봉투를 반환하고 DB에 1건 저장한다")
    void createSession_returns201AndPersists() throws Exception {
        mockMvc.perform(post("/api/v1/sessions"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.startedAt").exists())
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.timestamp").exists());

        assertThat(sessionRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("GET /api/v1/sessions/{id} - 존재하면 200과 세션 봉투를 반환한다")
    void getSession_returns200_whenExists() throws Exception {
        Session saved = sessionRepository.save(Session.start(Instant.parse("2026-06-28T00:00:00Z")));

        mockMvc.perform(get("/api/v1/sessions/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(saved.getId()))
                .andExpect(jsonPath("$.data.status").value(SessionStatus.ACTIVE.name()))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/v1/sessions/{id} - 없으면 404와 SESSION_NOT_FOUND 에러 봉투를 반환한다")
    void getSession_returns404_whenMissing() throws Exception {
        mockMvc.perform(get("/api/v1/sessions/{id}", 999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("GET /api/v1/sessions?status= - 상태 필터로 해당 상태 세션만 반환한다")
    void getSessions_filtersByStatus() throws Exception {
        sessionRepository.save(Session.start(NOW));
        sessionRepository.save(Session.start(NOW));
        Session completed = Session.start(NOW);
        completed.complete(NOW);
        sessionRepository.save(completed);

        mockMvc.perform(get("/api/v1/sessions").param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.error").doesNotExist());

        mockMvc.perform(get("/api/v1/sessions").param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/sessions?size= - Offset 페이지 메타(totalElements/hasNext)를 반환한다")
    void getSessions_paginates() throws Exception {
        sessionRepository.save(Session.start(NOW));
        sessionRepository.save(Session.start(NOW));
        sessionRepository.save(Session.start(NOW));

        mockMvc.perform(get("/api/v1/sessions").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/sessions?participantId= - 참여자 필터로 해당 참여자의 세션만 반환한다")
    void getSessions_filtersByParticipant() throws Exception {
        Session withParticipant = sessionRepository.save(Session.start(NOW));
        sessionRepository.save(Session.start(NOW));
        participantRepository.save(SessionParticipant.join(withParticipant.getId(), "u1", NOW));

        mockMvc.perform(get("/api/v1/sessions").param("participantId", "u1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(withParticipant.getId()));

        mockMvc.perform(get("/api/v1/sessions").param("participantId", "nobody"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("GET /api/v1/sessions?from= - 미래 시각 필터는 빈 목록을 반환한다")
    void getSessions_filtersByPeriod() throws Exception {
        sessionRepository.save(Session.start(NOW));

        mockMvc.perform(get("/api/v1/sessions").param("from", "2999-01-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }
}
