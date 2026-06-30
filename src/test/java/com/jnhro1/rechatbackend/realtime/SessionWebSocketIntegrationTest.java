package com.jnhro1.rechatbackend.realtime;
import com.jnhro1.rechatbackend.session.Session;
import com.jnhro1.rechatbackend.session.SessionRepository;
import com.jnhro1.rechatbackend.participant.SessionParticipant;
import com.jnhro1.rechatbackend.participant.SessionParticipantRepository;
import com.jnhro1.rechatbackend.event.SessionEvent;
import com.jnhro1.rechatbackend.event.SessionEventRepository;
import com.jnhro1.rechatbackend.event.message.SessionMessageRepository;

import static org.assertj.core.api.Assertions.assertThat;

import com.jnhro1.rechatbackend.event.enums.EventType;
import com.jnhro1.rechatbackend.participant.enums.PresenceStatus;
import com.jnhro1.rechatbackend.support.WebSocketIntegrationTestBase;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;

class SessionWebSocketIntegrationTest extends WebSocketIntegrationTestBase {

    private static final Instant NOW = Instant.parse("2026-06-29T00:00:00Z");
    private static final String OCCURRED = "2026-06-29T00:00:00Z";

    @Autowired
    private SessionRepository sessionRepository;
    @Autowired
    private SessionParticipantRepository participantRepository;
    @Autowired
    private SessionEventRepository eventRepository;
    @Autowired
    private SessionMessageRepository messageRepository;

    private final List<StompSession> sessions = new ArrayList<>();

    @AfterEach
    void tearDown() {
        sessions.forEach(s -> {
            if (s.isConnected()) {
                s.disconnect();
            }
        });
        messageRepository.deleteAllInBatch();
        eventRepository.deleteAllInBatch();
        participantRepository.deleteAllInBatch();
        sessionRepository.deleteAllInBatch();
    }

    private Long sessionWithParticipant(String userId) {
        Long sessionId = sessionRepository.save(Session.start(NOW)).getId();
        participantRepository.save(SessionParticipant.join(sessionId, userId, NOW));
        return sessionId;
    }

    private StompSession open(String userId, Long sessionId) throws Exception {
        StompSession session = connect(userId, sessionId);
        sessions.add(session);
        return session;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private BlockingQueue<Map<String, Object>> subscribe(StompSession session, String destination) throws Exception {
        BlockingQueue<Map<String, Object>> queue = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.add((Map<String, Object>) payload);
            }
        });
        Thread.sleep(300); // SUBSCRIBE 등록 대기
        return queue;
    }

    private void send(StompSession session, Long sessionId, String eventId, String content) {
        session.send("/app/sessions/" + sessionId + "/send",
                Map.of("eventId", eventId, "content", content, "occurredAt", OCCURRED));
    }

    @Test
    @DisplayName("WS - 참여하지 않은 다른 세션으로 보내면 저장되지 않는다(세션 격리)")
    void wsSend_crossSession_notPersisted() throws Exception {
        Long sessionA = sessionWithParticipant("alice");                    // alice는 A 참여
        Long sessionB = sessionRepository.save(Session.start(NOW)).getId(); // alice는 B 비참여
        StompSession alice = open("alice", sessionA);

        send(alice, sessionB, "x1", "intrude"); // 자기가 속하지 않은 B로 침범 전송
        Thread.sleep(1000);                     // 서버가 처리(거부)할 시간

        assertThat(eventRepository
                .findBySessionIdAndServerSequenceGreaterThanOrderByServerSequenceAsc(sessionB, 0L)).isEmpty();
    }

    @Test
    @DisplayName("WS send - 메시지를 보내면 토픽 구독자가 수신하고 DB에 이벤트/메시지가 저장된다")
    void send_broadcastsAndPersists() throws Exception {
        Long sessionId = sessionWithParticipant("alice");
        StompSession alice = open("alice", sessionId);
        BlockingQueue<Map<String, Object>> received = subscribe(alice, "/topic/sessions/" + sessionId);

        send(alice, sessionId, "e1", "hi");

        Map<String, Object> frame = received.poll(5, TimeUnit.SECONDS);
        assertThat(frame).isNotNull();
        assertThat(frame.get("type")).isEqualTo("MESSAGE");
        assertThat(frame.get("serverSequence")).isEqualTo(1);
        assertThat(((Map<?, ?>) frame.get("payload")).get("content")).isEqualTo("hi");
        assertThat(eventRepository.count()).isEqualTo(1);
        assertThat(messageRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("WS 1:1 - 한쪽이 보내면 상대 참여자가 실시간 수신한다")
    void send_isReceivedByOtherParticipant() throws Exception {
        Long sessionId = sessionWithParticipant("alice");
        participantRepository.save(SessionParticipant.join(sessionId, "bob", NOW));
        StompSession alice = open("alice", sessionId);
        StompSession bob = open("bob", sessionId);
        BlockingQueue<Map<String, Object>> bobInbox = subscribe(bob, "/topic/sessions/" + sessionId);

        send(alice, sessionId, "e1", "hello bob");

        Map<String, Object> frame = bobInbox.poll(5, TimeUnit.SECONDS);
        assertThat(frame).isNotNull();
        assertThat(((Map<?, ?>) frame.get("payload")).get("content")).isEqualTo("hello bob");
        assertThat(frame.get("senderId")).isEqualTo("alice");
    }

    @Test
    @DisplayName("WS 구독 인가 - 비참여자는 세션 토픽을 구독해도 메시지를 받지 못한다")
    void subscribe_nonParticipant_isDenied() throws Exception {
        Long sessionId = sessionWithParticipant("bob");      // bob만 참여
        StompSession bob = open("bob", sessionId);
        StompSession alice = open("alice", sessionId);        // alice는 연결만, 미참여
        BlockingQueue<Map<String, Object>> bobInbox = subscribe(bob, "/topic/sessions/" + sessionId);
        BlockingQueue<Map<String, Object>> aliceInbox = subscribe(alice, "/topic/sessions/" + sessionId);

        send(bob, sessionId, "e1", "secret");

        assertThat(bobInbox.poll(5, TimeUnit.SECONDS)).isNotNull();   // 참여자는 수신
        assertThat(aliceInbox.poll(1, TimeUnit.SECONDS)).isNull();    // 비참여자는 미수신(구독 거부)
    }

    @Test
    @DisplayName("WS resume 인가 - 비참여자의 resume은 누락 이벤트를 받지 못한다")
    void resume_nonParticipant_isDenied() throws Exception {
        Long sessionId = sessionWithParticipant("bob");
        eventRepository.save(SessionEvent.of(sessionId, 1, "e1", "bob", EventType.MESSAGE, "{\"content\":\"a\"}", NOW));
        StompSession alice = open("alice", sessionId);        // 미참여
        // 개인 큐 구독은 막지 않음(자기 큐) — 그러나 resume은 참여자 검사에서 차단된다.
        BlockingQueue<Map<String, Object>> personal = subscribe(alice, "/user/queue/sessions/" + sessionId);

        alice.send("/app/sessions/" + sessionId + "/resume", Map.of("afterSequence", 0));

        assertThat(personal.poll(2, TimeUnit.SECONDS)).isNull(); // 누락 이벤트를 받지 못함
    }

    @Test
    @DisplayName("WS resume - afterSequence 이후 누락 이벤트를 개인 큐로 리플레이한다")
    void resume_replaysMissedEvents() throws Exception {
        Long sessionId = sessionWithParticipant("alice");
        eventRepository.save(SessionEvent.of(sessionId, 1, "e1", "alice", EventType.MESSAGE, "{\"content\":\"a\"}", NOW));
        eventRepository.save(SessionEvent.of(sessionId, 2, "e2", "alice", EventType.MESSAGE, "{\"content\":\"b\"}", NOW));
        StompSession alice = open("alice", sessionId);
        BlockingQueue<Map<String, Object>> personal = subscribe(alice, "/user/queue/sessions/" + sessionId);

        alice.send("/app/sessions/" + sessionId + "/resume", Map.of("afterSequence", 0));

        Map<String, Object> first = personal.poll(5, TimeUnit.SECONDS);
        Map<String, Object> second = personal.poll(5, TimeUnit.SECONDS);
        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(first.get("serverSequence")).isEqualTo(1);
        assertThat(second.get("serverSequence")).isEqualTo(2);
    }

    @Test
    @DisplayName("WS presence - 참여자 연결이 끊기면 상대가 DISCONNECT 이벤트를 수신한다")
    void disconnect_broadcastsPresenceEvent() throws Exception {
        Long sessionId = sessionWithParticipant("alice");
        participantRepository.save(SessionParticipant.join(sessionId, "bob", NOW));
        StompSession alice = open("alice", sessionId);
        StompSession bob = open("bob", sessionId);
        BlockingQueue<Map<String, Object>> aliceInbox = subscribe(alice, "/topic/sessions/" + sessionId);

        bob.disconnect(); // 연결 종료 → DISCONNECT presence 이벤트

        Map<String, Object> frame = aliceInbox.poll(5, TimeUnit.SECONDS);
        assertThat(frame).isNotNull();
        assertThat(frame.get("type")).isEqualTo("DISCONNECT");
        assertThat(frame.get("senderId")).isEqualTo("bob");
    }

    @Test
    @DisplayName("WS presence - 끊겼다가 다시 연결하면 참여자가 ONLINE으로 자동 복귀한다")
    void reconnect_afterDisconnect_setsOnline() throws Exception {
        Long sessionId = sessionWithParticipant("alice"); // ONLINE
        StompSession first = open("alice", sessionId);
        first.disconnect();
        awaitPresence(sessionId, "alice", PresenceStatus.OFFLINE);

        open("alice", sessionId); // 재연결 → RECONNECT 자동 발생

        awaitPresence(sessionId, "alice", PresenceStatus.ONLINE);
    }

    private void awaitPresence(Long sessionId, String userId, PresenceStatus expected) throws Exception {
        for (int i = 0; i < 50; i++) {
            var p = participantRepository.findBySessionIdAndUserId(sessionId, userId);
            if (p.isPresent() && p.get().getPresence() == expected) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("presence가 " + expected + "로 바뀌지 않음");
    }
}
