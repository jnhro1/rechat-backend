package com.jnhro1.rechatbackend.participant.exception;

import com.jnhro1.rechatbackend.common.exception.BusinessException;
import com.jnhro1.rechatbackend.common.exception.ErrorCode;

public class SessionFullException extends BusinessException {

    public SessionFullException(Long sessionId) {
        super(ErrorCode.SESSION_FULL, "세션 ID %d의 정원이 가득 찼습니다.".formatted(sessionId));
    }
}
