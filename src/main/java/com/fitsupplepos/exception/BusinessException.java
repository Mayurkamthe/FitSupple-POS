package com.fitsupplepos.exception;

/** Base class for domain/business-rule violations shown to the owner as friendly messages. */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
