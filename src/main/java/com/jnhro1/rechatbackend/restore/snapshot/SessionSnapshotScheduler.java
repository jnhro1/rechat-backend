package com.jnhro1.rechatbackend.restore.snapshot;

import com.jnhro1.rechatbackend.event.SessionEvent;
import com.jnhro1.rechatbackend.event.SessionEventAppended;
import com.jnhro1.rechatbackend.restore.snapshot.SessionSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class SessionSnapshotScheduler {

    private final SessionSnapshotService snapshotService;

    @Value("${snapshot.interval:50}")
    private int interval;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEventAppended(SessionEventAppended appended) {
        SessionEvent event = appended.event();
        long sequence = event.getServerSequence();
        if (interval > 0 && sequence % interval == 0) {
            snapshotService.create(event.getSessionId(), sequence);
        }
    }
}
