package com.example.leavemanagement.exception;

/** Operation is not allowed in the resource's current state (e.g. approving an approved request) -> 409. */
public class InvalidStateException extends RuntimeException {
    public InvalidStateException(String message) { super(message); }
}
