package com.sufiyan.moviebooking.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base class for expected, client-facing failures. {@code errorCode} is a stable machine-readable code.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public BusinessException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }
}
