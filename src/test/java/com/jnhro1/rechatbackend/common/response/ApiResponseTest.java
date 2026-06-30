package com.jnhro1.rechatbackend.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.jnhro1.rechatbackend.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApiResponseTest {

    @Test
    @DisplayName("success - data를 채우고 error는 null, timestamp 포함")
    void success_fillsDataAndNullError() {
        ApiResponse<String> response = ApiResponse.success("ok");

        assertThat(response.data()).isEqualTo("ok");
        assertThat(response.error()).isNull();
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    @DisplayName("failure - error를 채우고 data는 null, timestamp 포함")
    void failure_fillsErrorAndNullData() {
        ErrorResponse error = ErrorResponse.of(ErrorCode.INTERNAL_ERROR);

        ApiResponse<Void> response = ApiResponse.failure(error);

        assertThat(response.data()).isNull();
        assertThat(response.error()).isEqualTo(error);
        assertThat(response.timestamp()).isNotNull();
    }
}
