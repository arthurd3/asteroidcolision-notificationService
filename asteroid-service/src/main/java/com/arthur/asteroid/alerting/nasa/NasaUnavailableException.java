package com.arthur.asteroid.alerting.nasa;

/**
 * The NASA feed could not be reached, refused the request, or returned something
 * unusable. Translated to a 503 by the API exception handler.
 */
public class NasaUnavailableException extends RuntimeException {

    public NasaUnavailableException(String message) {
        super(message);
    }

    public NasaUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
