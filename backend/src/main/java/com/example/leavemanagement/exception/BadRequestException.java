package com.example.leavemanagement.exception;

/** Business-level input validation that can't be expressed with Bean Validation -> 400. */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) { super(message); }
}
