package com.arthur.asteroid.alerting.nasa.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DiameterRange(

        @JsonProperty("estimated_diameter_min") double min,
        @JsonProperty("estimated_diameter_max") double max
) {

    public double average() {
        return (min + max) / 2;
    }
}
