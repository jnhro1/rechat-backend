package com.jnhro1.rechatbackend.common.exception;

import com.jnhro1.rechatbackend.common.response.ApiResponse;
import com.jnhro1.rechatbackend.common.response.ErrorResponse;
import com.jnhro1.rechatbackend.common.response.ErrorResponse.FieldError;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("비즈니스 예외: code={}, message={}", errorCode.getCode(), e.getMessage());
        return toResponse(errorCode, ErrorResponse.of(errorCode, e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        List<FieldError> details = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> FieldError.of(fe.getField(), fe.getDefaultMessage()))
                .toList();
        log.info("요청 검증 실패: {} 건", details.size());
        return toResponse(ErrorCode.VALIDATION_ERROR,
                ErrorResponse.of(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getDefaultMessage(), details));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
        log.info("제약 조건 위반: {}", e.getMessage());
        return toResponse(ErrorCode.CONSTRAINT_VIOLATION,
                ErrorResponse.of(ErrorCode.CONSTRAINT_VIOLATION, e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        log.info("요청 본문 파싱 실패: {}", e.getMessage());
        return toResponse(ErrorCode.MALFORMED_REQUEST, ErrorResponse.of(ErrorCode.MALFORMED_REQUEST));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.info("파라미터 타입 불일치: name={}, value={}", e.getName(), e.getValue());
        return toResponse(ErrorCode.TYPE_MISMATCH, ErrorResponse.of(ErrorCode.TYPE_MISMATCH));
    }

    /** DB 무결성 위반(Unique Constraint 충돌 등) — 멱등성/중복 처리의 안전망. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("데이터 무결성 위반: {}", e.getMostSpecificCause().getMessage());
        return toResponse(ErrorCode.DATA_INTEGRITY_VIOLATION, ErrorResponse.of(ErrorCode.DATA_INTEGRITY_VIOLATION));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("예상치 못한 예외", e);
        return toResponse(ErrorCode.INTERNAL_ERROR, ErrorResponse.of(ErrorCode.INTERNAL_ERROR));
    }

    private ResponseEntity<ApiResponse<Void>> toResponse(ErrorCode errorCode, ErrorResponse body) {
        return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.failure(body));
    }
}
