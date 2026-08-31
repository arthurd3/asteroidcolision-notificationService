package com.arthur.asteroid.webui.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** One page of the full catalogue - upwards of 62,000 objects. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NeoBrowseView(
        @JsonProperty("near_earth_objects") List<AsteroidView> nearEarthObjects,
        PageInfoView page) {

    public List<AsteroidView> nearEarthObjects() {
        return nearEarthObjects == null ? List.of() : nearEarthObjects;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PageInfoView(int size,
                               @JsonProperty("total_elements") long totalElements,
                               @JsonProperty("total_pages") int totalPages,
                               int number) {
    }
}
