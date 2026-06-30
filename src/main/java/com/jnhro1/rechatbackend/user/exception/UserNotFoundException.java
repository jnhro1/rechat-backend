package com.jnhro1.rechatbackend.user.exception;

import com.jnhro1.rechatbackend.common.exception.BusinessException;
import com.jnhro1.rechatbackend.common.exception.ErrorCode;

public class UserNotFoundException extends BusinessException {

    public UserNotFoundException(String userId) {
        super(ErrorCode.USER_NOT_FOUND, "유저 '%s'를 찾을 수 없습니다.".formatted(userId));
    }
}
