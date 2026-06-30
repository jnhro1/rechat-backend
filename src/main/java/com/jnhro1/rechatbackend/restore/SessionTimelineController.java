package com.jnhro1.rechatbackend.restore;

import com.jnhro1.rechatbackend.common.exception.ApiErrorCodes;
import com.jnhro1.rechatbackend.common.exception.ErrorCode;
import com.jnhro1.rechatbackend.common.response.ApiResponse;
import com.jnhro1.rechatbackend.restore.response.TimelineResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/timeline")
@RequiredArgsConstructor
@Validated
@Tag(name = "SessionTimeline", description = "세션 상태 복원 API")
public class SessionTimelineController {

    private final SessionTimelineService sessionTimelineService;

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "특정 시점 상태 복원",
            description = "at(미지정 시 현재) 시점까지의 이벤트를 리플레이해 참여자+메시지 상태를 복원한다.")
    @ApiErrorCodes(ErrorCode.SESSION_NOT_FOUND)
    public ApiResponse<TimelineResponse> getTimeline(
            @PathVariable Long sessionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant at,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int messageLimit
    ) {
        return ApiResponse.success(sessionTimelineService.reconstruct(sessionId, at, messageLimit));
    }
}
