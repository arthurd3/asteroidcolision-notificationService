package com.arthur.asteroid.alerting.nasa.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EstimatedDiameter(

        @JsonProperty("meters") DiameterRange meters
) {
}
