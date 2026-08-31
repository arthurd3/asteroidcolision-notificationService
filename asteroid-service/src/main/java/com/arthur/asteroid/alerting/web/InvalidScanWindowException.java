package com.arthur.asteroid.alerting.web;

/** The caller asked for a date range the NASA feed will not accept. */
public class InvalidScanWindowException extends RuntimeException {

    public InvalidScanWindowException(String message) {
        super(message);
    }
}
