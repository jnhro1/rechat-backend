package com.jnhro1.rechatbackend.event.exception;

import com.jnhro1.rechatbackend.common.exception.BusinessException;
import com.jnhro1.rechatbackend.common.exception.ErrorCode;
import com.jnhro1.rechatbackend.event.enums.EventType;

public class UnsupportedEventTypeException extends BusinessException {

    public UnsupportedEventTypeException(EventType type) {
        super(ErrorCode.UNSUPPORTED_EVENT_TYPE, "지원하지 않는 이벤트 종류입니다: %s".formatted(type));
    }
}
