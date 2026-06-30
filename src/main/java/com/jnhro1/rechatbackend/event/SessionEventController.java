package com.jnhro1.rechatbackend.event;

import com.jnhro1.rechatbackend.common.exception.ApiErrorCodes;
import com.jnhro1.rechatbackend.common.exception.ErrorCode;
import com.jnhro1.rechatbackend.common.response.ApiResponse;
import com.jnhro1.rechatbackend.event.request.CollectEventRequest;
import com.jnhro1.rechatbackend.event.response.EventRangeResponse;
import com.jnhro1.rechatbackend.event.response.SessionEventResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/events")
@RequiredArgsConstructor
@Validated
@Tag(name = "SessionEvent", description = "세션 이벤트 수집/조회 API")
public class SessionEventController {

    private final SessionEventService sessionEventService;

    @PostMapping
    @Operation(summary = "이벤트 수집", description = "MESSAGE 이벤트를 수집한다. 동일 eventId 재요청은 멱등(최초 201, 중복 200).")
    @ApiErrorCodes({
            ErrorCode.SESSION_NOT_FOUND,
            ErrorCode.SESSION_NOT_ACTIVE,
            ErrorCode.SENDER_NOT_PARTICIPANT,
            ErrorCode.UNSUPPORTED_EVENT_TYPE
    })
    public ResponseEntity<ApiResponse<SessionEventResponse>> collect(
            @PathVariable Long sessionId,
            @Valid @RequestBody CollectEventRequest request
    ) {
        // FYI: 멱등 재요청과 신규 저장을 상태코드로 구분(중복 200 / 신규 201)하기 위해 ResponseEntity 사용.
        CollectEventResult result = sessionEventService.collect(sessionId, request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.success(result.event()));
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "이벤트 구간 조회",
            description = "occurred_at이 [from, to] 범위인 이벤트를 server_sequence 오름차순으로 조회한다"
                    + "(디버깅/검증/리플레이). from·to는 선택(미지정 시 해당 경계 무시).")
    @ApiErrorCodes(ErrorCode.SESSION_NOT_FOUND)
    public ApiResponse<EventRangeResponse> getEvents(
            @PathVariable Long sessionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit
    ) {
        return ApiResponse.success(sessionEventService.getEvents(sessionId, from, to, limit));
    }
}
