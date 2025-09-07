package com.tspoon.core.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BusinessException extends RuntimeException {
    
    private final int status;
    
    public BusinessException(String message) {
        super(message);
        this.status = HttpStatus.BAD_REQUEST.value();
    }
    
    public BusinessException(String message, HttpStatus status) {
        super(message);
        this.status = status.value();
    }
    
    public BusinessException(String message, int status) {
        super(message);
        this.status = status;
    }
}
