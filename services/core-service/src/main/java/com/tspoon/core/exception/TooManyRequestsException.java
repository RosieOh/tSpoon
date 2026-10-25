package com.tspoon.core.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.time.Duration;

/**
 * 시도 횟수 제한에 걸렸다 (429). {@code retryAfter} 는 Retry-After 헤더(초)로 나간다.
 */
@Getter
public class TooManyRequestsException extends BusinessException {

    private final Duration retryAfter;

    public TooManyRequestsException(String message, String code, Duration retryAfter) {
        super(message, HttpStatus.TOO_MANY_REQUESTS, code);
        this.retryAfter = retryAfter;
    }
}
