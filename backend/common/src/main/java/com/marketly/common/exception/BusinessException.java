package com.marketly.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base class for all application-level business exceptions.
 * Each subclass maps to a specific HTTP status and error code.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    public BusinessException(String errorCode, String message, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
}
