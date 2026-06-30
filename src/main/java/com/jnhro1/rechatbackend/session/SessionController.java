package com.jnhro1.rechatbackend.session;
import com.jnhro1.rechatbackend.participant.SessionParticipantService;

import com.jnhro1.rechatbackend.common.exception.ApiErrorCodes;
import com.jnhro1.rechatbackend.common.exception.ErrorCode;
import com.jnhro1.rechatbackend.common.response.ApiResponse;
import com.jnhro1.rechatbackend.common.response.PageResponse;
import com.jnhro1.rechatbackend.common.util.IdempotencyKeyResolver;
import com.jnhro1.rechatbackend.session.enums.SessionSortType;
import com.jnhro1.rechatbackend.session.enums.SessionStatus;
import com.jnhro1.rechatbackend.participant.request.ParticipantRequest;
import com.jnhro1.rechatbackend.session.request.SessionSearchCondition;
import com.jnhro1.rechatbackend.participant.response.SessionParticipantResponse;
import com.jnhro1.rechatbackend.session.response.SessionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
@Validated
@Tag(name = "Session", description = "채팅 세션 API")
public class SessionController {

    private final SessionService sessionService;
    private final SessionParticipantService sessionParticipantService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "세션 생성", description = "빈 ACTIVE 세션을 생성한다. 참여자는 이후 join으로 추가한다.")
    public ApiResponse<SessionResponse> createSession() {
        return ApiResponse.success(sessionService.createSession());
    }

    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "세션 단건 조회", description = "세션 ID로 단건 조회한다. 없으면 404.")
    @ApiErrorCodes(ErrorCode.SESSION_NOT_FOUND)
    public ApiResponse<SessionResponse> getSession(@PathVariable Long id) {
        return ApiResponse.success(sessionService.getById(id));
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "세션 목록 조회", description = "상태/기간/참여자 필터 + 정렬 + Offset 페이지네이션으로 세션을 조회한다.")
    public ApiResponse<PageResponse<SessionResponse>> getSessions(
            @RequestParam(required = false) SessionStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String participantId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "CREATED_DESC") SessionSortType sort
    ) {
        SessionSearchCondition condition = SessionSearchCondition.of(status, from, to, participantId);
        return ApiResponse.success(sessionService.search(condition, page, size, sort));
    }

    @PostMapping("/{id}/join")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "세션 참여", description = "JOIN 이벤트를 기록하고 참여자를 ONLINE으로 만든다(멱등). 1:1 정원 초과 시 409.")
    @ApiErrorCodes({
            ErrorCode.SESSION_NOT_FOUND,
            ErrorCode.SESSION_NOT_ACTIVE,
            ErrorCode.USER_NOT_FOUND,
            ErrorCode.SESSION_FULL
    })
    public ApiResponse<SessionParticipantResponse> join(
            @PathVariable Long id,
            @Valid @RequestBody ParticipantRequest request
    ) {
        String eventId = IdempotencyKeyResolver.resolve(request.eventId());
        return ApiResponse.success(sessionParticipantService.join(id, request.userId(), eventId));
    }

    @PostMapping("/{id}/leave")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "세션 퇴장", description = "LEAVE 이벤트를 기록하고 참여자를 OFFLINE + leftAt으로 만든다(멱등).")
    @ApiErrorCodes({ErrorCode.SESSION_NOT_FOUND, ErrorCode.PARTICIPANT_NOT_FOUND})
    public ApiResponse<SessionParticipantResponse> leave(
            @PathVariable Long id,
            @Valid @RequestBody ParticipantRequest request
    ) {
        String eventId = IdempotencyKeyResolver.resolve(request.eventId());
        return ApiResponse.success(sessionParticipantService.leave(id, request.userId(), eventId));
    }

    @PostMapping("/{id}/disconnect")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "연결 끊김", description = "DISCONNECT 이벤트를 기록하고 presence를 OFFLINE으로 만든다(멤버십 유지, 멱등).")
    @ApiErrorCodes({ErrorCode.SESSION_NOT_FOUND, ErrorCode.PARTICIPANT_NOT_FOUND})
    public ApiResponse<SessionParticipantResponse> disconnect(
            @PathVariable Long id,
            @Valid @RequestBody ParticipantRequest request
    ) {
        String eventId = IdempotencyKeyResolver.resolve(request.eventId());
        return ApiResponse.success(sessionParticipantService.disconnect(id, request.userId(), eventId));
    }

    @PostMapping("/{id}/reconnect")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "재연결", description = "RECONNECT 이벤트를 기록하고 presence를 ONLINE으로 복귀시킨다(멱등).")
    @ApiErrorCodes({ErrorCode.SESSION_NOT_FOUND, ErrorCode.PARTICIPANT_NOT_FOUND})
    public ApiResponse<SessionParticipantResponse> reconnect(
            @PathVariable Long id,
            @Valid @RequestBody ParticipantRequest request
    ) {
        String eventId = IdempotencyKeyResolver.resolve(request.eventId());
        return ApiResponse.success(sessionParticipantService.reconnect(id, request.userId(), eventId));
    }

    @PostMapping("/{id}/end")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "세션 종료", description = "세션을 COMPLETED로 전이하고 남은 참여자를 전원 OFFLINE으로 만든다(멱등).")
    @ApiErrorCodes(ErrorCode.SESSION_NOT_FOUND)
    public ApiResponse<SessionResponse> end(@PathVariable Long id) {
        return ApiResponse.success(sessionService.end(id));
    }
}
