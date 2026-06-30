package com.jnhro1.rechatbackend.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.jnhro1.rechatbackend.common.exception.ErrorCode;
import com.jnhro1.rechatbackend.common.response.ErrorResponse.FieldError;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ErrorResponseTest {

    @Test
    @DisplayName("of(ErrorCode) - 코드/기본 메시지를 매핑하고 details는 빈 리스트")
    void of_mapsCodeAndDefaultMessage() {
        ErrorResponse error = ErrorResponse.of(ErrorCode.VALIDATION_ERROR);

        assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
        assertThat(error.message()).isEqualTo(ErrorCode.VALIDATION_ERROR.getDefaultMessage());
        assertThat(error.details()).isEmpty();
    }

    @Test
    @DisplayName("of(ErrorCode, message) - 커스텀 메시지로 덮어쓴다")
    void of_overridesMessage() {
        ErrorResponse error = ErrorResponse.of(ErrorCode.VALIDATION_ERROR, "커스텀 메시지");

        assertThat(error.message()).isEqualTo("커스텀 메시지");
        assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
    }

    @Test
    @DisplayName("of(ErrorCode, message, details) - 필드 오류 목록을 담는다")
    void of_carriesFieldDetails() {
        List<FieldError> details = List.of(FieldError.of("name", "필수입니다."));

        ErrorResponse error = ErrorResponse.of(ErrorCode.VALIDATION_ERROR, "검증 실패", details);

        assertThat(error.details()).containsExactly(FieldError.of("name", "필수입니다."));
    }
}
