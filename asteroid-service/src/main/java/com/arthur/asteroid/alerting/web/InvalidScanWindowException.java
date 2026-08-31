package com.arthur.asteroid.alerting.web;

/** The caller asked for a date range the NASA feed will not accept. */
public class InvalidScanWindowException extends InvalidRequestException {

    public InvalidScanWindowException(final String message) {
        super("Invalid scan window", "invalid-scan-window", message);
    }
}
