package com.jnhro1.rechatbackend.session.exception;

import com.jnhro1.rechatbackend.common.exception.BusinessException;
import com.jnhro1.rechatbackend.common.exception.ErrorCode;

public class SessionNotActiveException extends BusinessException {

    public SessionNotActiveException(Long sessionId) {
        super(ErrorCode.SESSION_NOT_ACTIVE, "세션 ID %d는 진행 중이 아닙니다.".formatted(sessionId));
    }
}
