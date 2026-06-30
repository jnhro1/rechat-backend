package com.jnhro1.rechatbackend.participant;
import com.jnhro1.rechatbackend.session.Session;
import com.jnhro1.rechatbackend.session.SessionRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jnhro1.rechatbackend.support.IntegrationTestBase;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
class SessionJoinApiIntegrationTest extends IntegrationTestBase {

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

    private ResultActions join(Long sessionId, String userId) throws Exception {
        return mockMvc.perform(post("/api/v1/sessions/{id}/join", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"%s\"}".formatted(userId)));
    }

    @Test
    @DisplayName("join - 시드된 유저가 참여하면 200과 ONLINE 참여자를 반환한다")
    void join_returns200_online() throws Exception {
        Long sessionId = sessionRepository.save(Session.start(NOW)).getId();

        join(sessionId, "alice")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value("alice"))
                .andExpect(jsonPath("$.data.presence").value("ONLINE"))
                .andExpect(jsonPath("$.error").doesNotExist());

        assertThat(participantRepository.countBySessionId(sessionId)).isEqualTo(1);
    }

    @Test
    @DisplayName("join - 같은 유저가 다시 참여해도 멱등(참여자 1명 유지)")
    void join_isIdempotent() throws Exception {
        Long sessionId = sessionRepository.save(Session.start(NOW)).getId();

        join(sessionId, "alice").andExpect(status().isOk());
        join(sessionId, "alice").andExpect(status().isOk());

        assertThat(participantRepository.countBySessionId(sessionId)).isEqualTo(1);
    }

    @Test
    @DisplayName("join - 3번째 참여는 409 SESSION_FULL (1:1 정원)")
    void join_returns409_whenFull() throws Exception {
        Long sessionId = sessionRepository.save(Session.start(NOW)).getId();

        join(sessionId, "alice").andExpect(status().isOk());
        join(sessionId, "bob").andExpect(status().isOk());
        join(sessionId, "carol")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SESSION_FULL"));
    }

    @Test
    @DisplayName("join - 정원이 찼어도 한 명이 leave하면 새 참여자가 들어올 수 있다(정원=현재 참여자 기준)")
    void join_freesSlot_afterLeave() throws Exception {
        Long sessionId = sessionRepository.save(Session.start(NOW)).getId();
        join(sessionId, "alice").andExpect(status().isOk());
        join(sessionId, "bob").andExpect(status().isOk());
        join(sessionId, "carol")  // 꽉 참 → 거부
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SESSION_FULL"));

        // bob 퇴장 → 자리 1칸 확보
        mockMvc.perform(post("/api/v1/sessions/{id}/leave", sessionId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"bob\"}"))
                .andExpect(status().isOk());

        join(sessionId, "carol").andExpect(status().isOk());  // 이제 입장 가능

        assertThat(participantRepository.countBySessionIdAndLeftAtIsNull(sessionId)).isEqualTo(2);
    }

    @Test
    @DisplayName("join - 존재하지 않는 유저는 404 USER_NOT_FOUND")
    void join_returns404_whenUserMissing() throws Exception {
        Long sessionId = sessionRepository.save(Session.start(NOW)).getId();

        join(sessionId, "ghost")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("join - 종료된 세션은 409 SESSION_NOT_ACTIVE")
    void join_returns409_whenSessionNotActive() throws Exception {
        Session completed = Session.start(NOW);
        completed.complete(NOW);
        Long sessionId = sessionRepository.save(completed).getId();

        join(sessionId, "alice")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_ACTIVE"));
    }

    @Test
    @DisplayName("join - 없는 세션은 404 SESSION_NOT_FOUND")
    void join_returns404_whenSessionMissing() throws Exception {
        join(999_999L, "alice")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("join 동시성 - 서로 다른 3명이 동시에 참여해도 정확히 2명만 성공한다")
    void join_concurrent_enforcesCapacity() throws Exception {
        Long sessionId = sessionRepository.save(Session.start(NOW)).getId();
        List<String> users = List.of("alice", "bob", "carol");

        ExecutorService pool = Executors.newFixedThreadPool(users.size());
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(users.size());
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger full = new AtomicInteger();

        for (String user : users) {
            pool.submit(() -> {
                try {
                    start.await();
                    int httpStatus = join(sessionId, user).andReturn().getResponse().getStatus();
                    if (httpStatus == 200) {
                        ok.incrementAndGet();
                    } else if (httpStatus == 409) {
                        full.incrementAndGet();
                    }
                } catch (Exception ignored) {
                    // 카운트되지 않은 결과는 아래 단언에서 실패로 드러난다.
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(ok.get()).isEqualTo(Session.MAX_PARTICIPANTS);
        assertThat(full.get()).isEqualTo(1);
        assertThat(participantRepository.countBySessionId(sessionId)).isEqualTo(Session.MAX_PARTICIPANTS);
    }
}
