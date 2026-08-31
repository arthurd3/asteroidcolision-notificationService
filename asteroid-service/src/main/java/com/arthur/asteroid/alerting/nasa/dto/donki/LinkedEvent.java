package com.arthur.asteroid.alerting.nasa.dto.donki;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A cross-reference from one space-weather event to another, by DONKI activity id.
 *
 * <p>This is how a geomagnetic storm points back at the coronal mass ejection that
 * caused it, which is the one genuinely interesting relationship in this data.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LinkedEvent(@JsonProperty("activityID") String activityId) {
}
