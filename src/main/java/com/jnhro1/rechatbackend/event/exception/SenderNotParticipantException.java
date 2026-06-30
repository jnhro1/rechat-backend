package com.jnhro1.rechatbackend.event.exception;

import com.jnhro1.rechatbackend.common.exception.BusinessException;
import com.jnhro1.rechatbackend.common.exception.ErrorCode;

public class SenderNotParticipantException extends BusinessException {

    public SenderNotParticipantException(Long sessionId, String userId) {
        super(ErrorCode.SENDER_NOT_PARTICIPANT,
                "유저 '%s'는 세션 ID %d의 참여자가 아닙니다.".formatted(userId, sessionId));
    }
}
