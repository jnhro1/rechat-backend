package com.jnhro1.rechatbackend.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jnhro1.rechatbackend.common.exception.ErrorCode;
import java.util.List;

public record ErrorResponse(
        String code,
        String message,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<FieldError> details
) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return of(errorCode, errorCode.getDefaultMessage());
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return of(errorCode, message, List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, String message, List<FieldError> details) {
        return new ErrorResponse(errorCode.getCode(), message, details);
    }

    public record FieldError(String field, String reason) {
        public static FieldError of(String field, String reason) {
            return new FieldError(field, reason);
        }
    }
}
