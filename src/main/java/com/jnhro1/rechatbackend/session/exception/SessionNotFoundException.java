package com.jnhro1.rechatbackend.session.exception;

import com.jnhro1.rechatbackend.common.exception.BusinessException;
import com.jnhro1.rechatbackend.common.exception.ErrorCode;

public class SessionNotFoundException extends BusinessException {

    public SessionNotFoundException(Long sessionId) {
        super(ErrorCode.SESSION_NOT_FOUND, "세션 ID %d를 찾을 수 없습니다.".formatted(sessionId));
    }
}
