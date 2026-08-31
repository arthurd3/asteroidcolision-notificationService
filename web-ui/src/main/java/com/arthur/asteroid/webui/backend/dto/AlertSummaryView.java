package com.arthur.asteroid.webui.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;

/** What a scan did: the one write this front end can trigger. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AlertSummaryView(LocalDate from, LocalDate to, int scanned, int hazardous, int published) {
}
