package com.jnhro1.rechatbackend.event.message;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionMessageRepository extends JpaRepository<SessionMessage, Long> {

    long countBySessionId(Long sessionId);
}
