package com.jnhro1.rechatbackend.event;
import com.jnhro1.rechatbackend.session.Session;
import com.jnhro1.rechatbackend.session.SessionRepository;
import com.jnhro1.rechatbackend.participant.SessionParticipant;
import com.jnhro1.rechatbackend.participant.SessionParticipantRepository;
import com.jnhro1.rechatbackend.event.message.SessionMessageRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
class SessionEventApiIntegrationTest extends IntegrationTestBase {

    private static final Instant NOW = Instant.parse("2026-06-28T00:00:00Z");
    private static final String OCCURRED = "2026-06-28T00:00:00Z";

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

    private Long activeSessionWith(String userId) {
        Long sessionId = sessionRepository.save(Session.start(NOW)).getId();
        participantRepository.save(SessionParticipant.join(sessionId, userId, NOW));
        return sessionId;
    }

    private ResultActions collect(Long sessionId, String eventId, String sender, String content, String occurredAt)
            throws Exception {
        String body = """
                {"eventId":"%s","type":"MESSAGE","senderId":"%s","content":"%s","occurredAt":"%s"}
                """.formatted(eventId, sender, content, occurredAt);
        return mockMvc.perform(post("/api/v1/sessions/{id}/events", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private List<SessionEvent> eventsOf(Long sessionId) {
        return eventRepository.findBySessionIdAndServerSequenceGreaterThanOrderByServerSequenceAsc(
                sessionId, 0L, PageRequest.of(0, 100));
    }

    @Test
    @DisplayName("collect - 최초 수집은 201, server_sequence=1, payload·projection 생성")
    void collect_returns201_andProjects() throws Exception {
        Long sessionId = activeSessionWith("alice");

        collect(sessionId, "e1", "alice", "hi", OCCURRED)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.serverSequence").value(1))
                .andExpect(jsonPath("$.data.eventId").value("e1"))
                .andExpect(jsonPath("$.data.payload.content").value("hi"))
                .andExpect(jsonPath("$.error").doesNotExist());

        assertThat(eventsOf(sessionId)).hasSize(1);
        assertThat(messageRepository.countBySessionId(sessionId)).isEqualTo(1);
    }

    @Test
    @DisplayName("collect - 연속 수집 시 server_sequence가 단조 증가한다")
    void collect_sequenceIncrements() throws Exception {
        Long sessionId = activeSessionWith("alice");

        collect(sessionId, "e1", "alice", "a", OCCURRED).andExpect(jsonPath("$.data.serverSequence").value(1));
        collect(sessionId, "e2", "alice", "b", OCCURRED).andExpect(jsonPath("$.data.serverSequence").value(2));
    }

    @Test
    @DisplayName("collect - 동일 eventId 재요청은 200과 기존 이벤트(같은 sequence) 반환, 저장 불변")
    void collect_duplicate_isIdempotent() throws Exception {
        Long sessionId = activeSessionWith("alice");

        collect(sessionId, "e1", "alice", "hi", OCCURRED).andExpect(status().isCreated());
        collect(sessionId, "e1", "alice", "hi", OCCURRED)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.serverSequence").value(1));

        assertThat(eventsOf(sessionId)).hasSize(1);
        assertThat(messageRepository.countBySessionId(sessionId)).isEqualTo(1);
    }

    @Test
    @DisplayName("collect - 참여자가 아니면 409 SENDER_NOT_PARTICIPANT")
    void collect_senderNotParticipant() throws Exception {
        Long sessionId = activeSessionWith("alice");

        collect(sessionId, "e1", "bob", "hi", OCCURRED)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SENDER_NOT_PARTICIPANT"));
    }

    @Test
    @DisplayName("collect - 다른 세션 참여자가 침범 전송하면 409 (세션 격리: 참여 검사는 해당 세션 scope)")
    void collect_crossSession_blocked() throws Exception {
        Long sessionA = activeSessionWith("alice"); // alice는 A에만 참여
        Long sessionB = activeSessionWith("bob");   // bob은 B에만 참여

        // alice가 자기가 속하지 않은 B 세션에 메시지 침범 시도
        collect(sessionB, "x1", "alice", "intrude", OCCURRED)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SENDER_NOT_PARTICIPANT"));

        assertThat(eventsOf(sessionB)).isEmpty(); // 침범 메시지는 원장에 저장되지 않음
    }

    @Test
    @DisplayName("collect - 비활성 세션이면 409 SESSION_NOT_ACTIVE")
    void collect_sessionNotActive() throws Exception {
        Session completed = Session.start(NOW);
        completed.complete(NOW);
        Long sessionId = sessionRepository.save(completed).getId();
        participantRepository.save(SessionParticipant.join(sessionId, "alice", NOW));

        collect(sessionId, "e1", "alice", "hi", OCCURRED)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_ACTIVE"));
    }

    @Test
    @DisplayName("collect - 없는 세션이면 404 SESSION_NOT_FOUND")
    void collect_sessionMissing() throws Exception {
        collect(999_999L, "e1", "alice", "hi", OCCURRED)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET events - 필터 없으면 전체를 server_sequence 오름차순으로 반환한다")
    void getEvents_returnsAllOrdered() throws Exception {
        Long sessionId = activeSessionWith("alice");
        collect(sessionId, "e1", "alice", "a", OCCURRED);
        collect(sessionId, "e2", "alice", "b", OCCURRED);
        collect(sessionId, "e3", "alice", "c", OCCURRED);

        mockMvc.perform(get("/api/v1/sessions/{id}/events", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events.length()").value(3))
                .andExpect(jsonPath("$.data.count").value(3))
                .andExpect(jsonPath("$.data.events[0].serverSequence").value(1))
                .andExpect(jsonPath("$.data.events[2].serverSequence").value(3))
                .andExpect(jsonPath("$.data.truncated").value(false))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    @DisplayName("GET events - occurred_at [from, to] 범위로 필터하고 server_sequence 순으로 반환한다")
    void getEvents_filtersByOccurredRange() throws Exception {
        Long sessionId = activeSessionWith("alice");
        collect(sessionId, "e1", "alice", "a", "2026-06-28T01:00:00Z"); // seq 1
        collect(sessionId, "e2", "alice", "b", "2026-06-28T02:00:00Z"); // seq 2
        collect(sessionId, "e3", "alice", "c", "2026-06-28T03:00:00Z"); // seq 3

        // from만: 02:00 이상 → e2, e3
        mockMvc.perform(get("/api/v1/sessions/{id}/events", sessionId).param("from", "2026-06-28T02:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(2))
                .andExpect(jsonPath("$.data.events[0].eventId").value("e2"))
                .andExpect(jsonPath("$.data.events[1].eventId").value("e3"));

        // to만: 02:00 이하 → e1, e2
        mockMvc.perform(get("/api/v1/sessions/{id}/events", sessionId).param("to", "2026-06-28T02:00:00Z"))
                .andExpect(jsonPath("$.data.count").value(2))
                .andExpect(jsonPath("$.data.events[0].eventId").value("e1"))
                .andExpect(jsonPath("$.data.events[1].eventId").value("e2"));

        // from+to 경계 포함: 정확히 02:00 → e2 한 건
        mockMvc.perform(get("/api/v1/sessions/{id}/events", sessionId)
                        .param("from", "2026-06-28T02:00:00Z").param("to", "2026-06-28T02:00:00Z"))
                .andExpect(jsonPath("$.data.count").value(1))
                .andExpect(jsonPath("$.data.events[0].eventId").value("e2"));
    }

    @Test
    @DisplayName("GET events - limit 상한에 걸리면 truncated=true로 잘림을 알린다")
    void getEvents_truncatesAtLimit() throws Exception {
        Long sessionId = activeSessionWith("alice");
        collect(sessionId, "e1", "alice", "a", OCCURRED);
        collect(sessionId, "e2", "alice", "b", OCCURRED);
        collect(sessionId, "e3", "alice", "c", OCCURRED);

        mockMvc.perform(get("/api/v1/sessions/{id}/events", sessionId).param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(2))
                .andExpect(jsonPath("$.data.events[0].serverSequence").value(1))
                .andExpect(jsonPath("$.data.events[1].serverSequence").value(2))
                .andExpect(jsonPath("$.data.truncated").value(true));
    }

    @Test
    @DisplayName("GET events - 없는 세션이면 404 SESSION_NOT_FOUND")
    void getEvents_sessionMissing() throws Exception {
        mockMvc.perform(get("/api/v1/sessions/{id}/events", 999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("순서 역전 - occurredAt이 거꾸로여도 server_sequence는 도착 순(결정적)")
    void ordering_followsArrivalNotOccurredAt() throws Exception {
        Long sessionId = activeSessionWith("alice");
        collect(sessionId, "late", "alice", "late-msg", "2026-06-28T10:00:00Z");   // 늦은 발생시각이 먼저 도착
        collect(sessionId, "early", "alice", "early-msg", "2026-06-28T01:00:00Z"); // 이른 발생시각이 나중 도착

        mockMvc.perform(get("/api/v1/sessions/{id}/events", sessionId))
                .andExpect(jsonPath("$.data.events[0].eventId").value("late"))   // server_sequence=1
                .andExpect(jsonPath("$.data.events[1].eventId").value("early")); // server_sequence=2
    }

    @Test
    @DisplayName("동시성 - 서로 다른 5건 동시 수집 시 server_sequence가 1..5로 유일·연속")
    void concurrent_distinctEvents_contiguousSequences() throws Exception {
        Long sessionId = activeSessionWith("alice");
        int n = 5;
        runConcurrently(n, i -> collect(sessionId, "e" + i, "alice", "m" + i, OCCURRED));

        List<Long> sequences = eventsOf(sessionId).stream().map(SessionEvent::getServerSequence).toList();
        assertThat(sequences).containsExactly(1L, 2L, 3L, 4L, 5L);
    }

    @Test
    @DisplayName("동시성 - 동일 eventId 5건 동시 수집 시 정확히 1건만 저장(멱등)")
    void concurrent_sameEventId_storesOnce() throws Exception {
        Long sessionId = activeSessionWith("alice");
        runConcurrently(5, i -> collect(sessionId, "dup", "alice", "m", OCCURRED));

        assertThat(eventsOf(sessionId)).hasSize(1);
    }

    private interface Task {
        void run(int index) throws Exception;
    }

    private void runConcurrently(int n, Task task) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);
        for (int i = 0; i < n; i++) {
            int idx = i;
            pool.submit(() -> {
                try {
                    start.await();
                    task.run(idx);
                } catch (Exception ignored) {
                    // 누락은 이후 DB 상태 단언에서 드러난다.
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await(15, TimeUnit.SECONDS);
        pool.shutdown();
    }
}
