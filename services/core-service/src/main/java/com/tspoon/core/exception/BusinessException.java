package com.tspoon.core.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 업무 규칙 위반. {@code code} 를 주면 ProblemDetail 의 {@code code} 속성으로 나가서,
 * 같은 상태 코드(예: 403)라도 클라이언트가 원인(이메일 미인증, 계정 정지 등)을 구분할 수 있다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public BusinessException(String message) {
        this(message, HttpStatus.BAD_REQUEST);
    }

    public BusinessException(String message, HttpStatus status) {
        this(message, status, null);
    }

    public BusinessException(String message, HttpStatus status, String code) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
