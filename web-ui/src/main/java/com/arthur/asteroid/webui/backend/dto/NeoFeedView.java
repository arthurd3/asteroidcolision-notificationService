package com.arthur.asteroid.webui.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.util.List;

/** A window of the feed, already flattened into date order by asteroid-service. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NeoFeedView(LocalDate from, LocalDate to, int elementCount, List<AsteroidView> objects) {

    public List<AsteroidView> objects() {
        return objects == null ? List.of() : objects;
    }

    public long hazardousCount() {
        return objects().stream().filter(AsteroidView::potentiallyHazardous).count();
    }
}
