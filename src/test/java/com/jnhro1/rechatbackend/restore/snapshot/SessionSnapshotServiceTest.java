package com.jnhro1.rechatbackend.restore.snapshot;
import com.jnhro1.rechatbackend.event.SessionEvent;
import com.jnhro1.rechatbackend.event.SessionEventRepository;
import com.jnhro1.rechatbackend.restore.SessionStateReducer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jnhro1.rechatbackend.event.enums.EventType;
import com.jnhro1.rechatbackend.restore.snapshot.SnapshotState;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionSnapshotServiceTest {

    private static final Long SESSION_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-06-28T00:00:00Z");
    private static final Instant LATER = Instant.parse("2026-06-28T00:05:00Z");

    @Mock
    private SessionSnapshotRepository snapshotRepository;
    @Mock
    private SessionEventRepository eventRepository;

    private ObjectMapper objectMapper;
    private SessionSnapshotService snapshotService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        snapshotService = new SessionSnapshotService(
                snapshotRepository, eventRepository, new SessionStateReducer(objectMapper), objectMapper);
    }

    @Test
    @DisplayName("create - events 1..uptoSequence를 fold해 상태를 저장한다")
    void create_foldsAndSaves() throws Exception {
        when(snapshotRepository.existsBySessionIdAndUptoSequence(SESSION_ID, 3L)).thenReturn(false);
        when(eventRepository.findBySessionIdAndServerSequenceLessThanEqualOrderByServerSequenceAsc(SESSION_ID, 3L))
                .thenReturn(List.of(
                        SessionEvent.of(SESSION_ID, 1, "p1", "alice", EventType.JOIN, null, NOW),
                        SessionEvent.of(SESSION_ID, 2, "p2", "bob", EventType.JOIN, null, NOW),
                        SessionEvent.of(SESSION_ID, 3, "m3", "alice", EventType.MESSAGE, "{\"content\":\"hi\"}", LATER)));

        snapshotService.create(SESSION_ID, 3L);

        ArgumentCaptor<SessionSnapshot> captor = ArgumentCaptor.forClass(SessionSnapshot.class);
        verify(snapshotRepository).save(captor.capture());
        SnapshotState state = objectMapper.readValue(captor.getValue().getState(), SnapshotState.class);
        assertThat(state.upToSequence()).isEqualTo(3L);
        assertThat(state.participants()).extracting("userId").containsExactly("alice", "bob");
        assertThat(state.messages()).extracting("content").containsExactly("hi");
        // watermark = 커버한 이벤트 중 최대 occurred_at
        assertThat(captor.getValue().getMaxOccurredAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("create - 이미 존재하면 멱등(fold·저장 없음)")
    void create_isIdempotent_whenExists() {
        when(snapshotRepository.existsBySessionIdAndUptoSequence(SESSION_ID, 3L)).thenReturn(true);

        snapshotService.create(SESSION_ID, 3L);

        verify(eventRepository, never())
                .findBySessionIdAndServerSequenceLessThanEqualOrderByServerSequenceAsc(any(), anyLong());
        verify(snapshotRepository, never()).save(any());
    }
}
