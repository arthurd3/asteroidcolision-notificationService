package com.arthur.asteroid.alerting.nasa.dto.epic;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The point on Earth directly under the spacecraft when the frame was taken. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EpicCoordinates(double lat, double lon) {
}
