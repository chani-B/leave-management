package com.example.leavemanagement.exception;

/** The request would push the employee over the annual vacation quota -> 422. */
public class InsufficientBalanceException extends RuntimeException {
    public InsufficientBalanceException(String message) { super(message); }
}
