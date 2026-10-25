package com.tspoon.core.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 모든 에러 응답을 RFC 9457 ProblemDetail(application/problem+json)로 내보낸다.
 * <pre>
 * {"type": "about:blank", "title": "Not Found", "status": 404, "detail": "게시글을 찾을 수 없습니다.", "instance": "/api/boards/1"}
 * </pre>
 * 입력값 검증 실패는 항목별 메시지를 {@code errors} 에 담는다.
 * Spring MVC 표준 예외(404, 405, 타입 변환 실패 등)는 ResponseEntityExceptionHandler 가 상태 코드를 정하고,
 * detail 은 tspoon-problem-messages.properties 의 한국어 문구를 쓴다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String INVALID_INPUT = "입력 데이터가 유효하지 않습니다.";

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusinessException(BusinessException ex) {
        log.debug("Business exception: {}", ex.getMessage());
        return toProblem(ex);
    }

    // 언제 다시 시도하면 되는지 Retry-After(초)로 알린다
    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ProblemDetail> handleTooManyRequests(TooManyRequestsException ex) {
        long seconds = Math.max(1, (ex.getRetryAfter().toMillis() + 999) / 1000);
        return ResponseEntity.status(ex.getStatus())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds))
                .body(toProblem(ex));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDeniedException(AccessDeniedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
    }

    // 동시 요청으로 유니크 제약(좋아요 중복 등)에 걸린 경우
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "이미 처리된 요청이거나 데이터 제약 조건에 위배됩니다.");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpectedException(Exception ex) {
        log.error("Unexpected error occurred", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");
    }

    // @Valid @RequestBody 검증 실패 — 필드별 메시지
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return invalidInput(ex.getBody(), errors, headers, status);
    }

    // @RequestParam 등 메서드 파라미터 검증 실패(@Min, @Max 등) — 파라미터별 메시지
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            HttpHeaders headers, HttpStatusCode status,
                                                                            WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(result -> result.getResolvableErrors().stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .findFirst()
                .ifPresent(message -> errors.putIfAbsent(result.getMethodParameter().getParameterName(), message)));
        return invalidInput(ex.getBody(), errors, headers, status);
    }

    private static ProblemDetail toProblem(BusinessException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        if (ex.getCode() != null) {
            problem.setProperty("code", ex.getCode());
        }
        return problem;
    }

    private static ResponseEntity<Object> invalidInput(ProblemDetail problem, Map<String, String> errors,
                                                       HttpHeaders headers, HttpStatusCode status) {
        problem.setDetail(INVALID_INPUT);
        problem.setProperty("errors", errors);
        return ResponseEntity.status(status).headers(headers).body(problem);
    }
}
