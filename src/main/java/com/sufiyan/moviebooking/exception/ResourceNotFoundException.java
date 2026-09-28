package com.sufiyan.moviebooking.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String resource, Object id) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", resource + " not found: " + id);
    }
}
