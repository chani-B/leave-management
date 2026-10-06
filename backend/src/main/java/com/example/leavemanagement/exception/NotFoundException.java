package com.example.leavemanagement.exception;

/** Requested resource does not exist -> 404. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) { super(message); }
}
