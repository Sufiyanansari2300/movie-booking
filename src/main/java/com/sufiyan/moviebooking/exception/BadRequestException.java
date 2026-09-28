package com.sufiyan.moviebooking.exception;

import org.springframework.http.HttpStatus;

/** Input that passes field validation but breaks a business rule (e.g. an impossible seat layout). */
public class BadRequestException extends BusinessException {

    public BadRequestException(String errorCode, String message) {
        super(HttpStatus.BAD_REQUEST, errorCode, message);
    }
}
