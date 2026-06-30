package com.jnhro1.rechatbackend.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통(프레임워크 예외 매핑)
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "요청 값이 올바르지 않습니다."),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "요청 본문을 해석할 수 없습니다."),
    TYPE_MISMATCH(HttpStatus.BAD_REQUEST, "TYPE_MISMATCH", "요청 파라미터 타입이 올바르지 않습니다."),
    CONSTRAINT_VIOLATION(HttpStatus.BAD_REQUEST, "CONSTRAINT_VIOLATION", "요청 제약 조건을 위반했습니다."),
    DATA_INTEGRITY_VIOLATION(HttpStatus.CONFLICT, "DATA_INTEGRITY_VIOLATION", "데이터 무결성 제약을 위반했습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "서버 내부 오류가 발생했습니다."),

    // 세션 도메인
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "세션을 찾을 수 없습니다."),
    SESSION_NOT_ACTIVE(HttpStatus.CONFLICT, "SESSION_NOT_ACTIVE", "진행 중이 아닌 세션입니다."),
    SESSION_FULL(HttpStatus.CONFLICT, "SESSION_FULL", "세션 정원이 가득 찼습니다."),
    SENDER_NOT_PARTICIPANT(HttpStatus.CONFLICT, "SENDER_NOT_PARTICIPANT", "세션 참여자가 아닙니다."),
    PARTICIPANT_NOT_FOUND(HttpStatus.NOT_FOUND, "PARTICIPANT_NOT_FOUND", "세션 참여자를 찾을 수 없습니다."),
    UNSUPPORTED_EVENT_TYPE(HttpStatus.BAD_REQUEST, "UNSUPPORTED_EVENT_TYPE", "지원하지 않는 이벤트 종류입니다."),

    // 유저 도메인
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "유저를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String defaultMessage;
}
