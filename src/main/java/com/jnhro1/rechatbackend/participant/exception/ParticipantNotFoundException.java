package com.jnhro1.rechatbackend.participant.exception;

import com.jnhro1.rechatbackend.common.exception.BusinessException;
import com.jnhro1.rechatbackend.common.exception.ErrorCode;

public class ParticipantNotFoundException extends BusinessException {

    public ParticipantNotFoundException(Long sessionId, String userId) {
        super(ErrorCode.PARTICIPANT_NOT_FOUND,
                "세션 ID %d에 유저 '%s' 참여자가 없습니다.".formatted(sessionId, userId));
    }
}
