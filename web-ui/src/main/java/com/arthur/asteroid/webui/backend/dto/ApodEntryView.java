package com.arthur.asteroid.webui.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/**
 * One Astronomy Picture of the Day, as asteroid-service serves it.
 *
 * <p>The helper methods are re-declared rather than inherited: asteroid-service
 * serialises a record's components and nothing else, so {@code media_type} arrives
 * on the wire but {@code image()} and {@code displayUrl()} do not.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApodEntryView(

        LocalDate date,
        String title,
        String explanation,
        String url,
        String hdurl,
        String copyright,
        @JsonProperty("media_type") String mediaType
) {

    public boolean image() {
        return "image".equals(mediaType);
    }

    public boolean video() {
        return "video".equals(mediaType);
    }

    /** APOD's own URLs carry no API key, so these can be linked straight from a page. */
    public String displayUrl() {
        return (hdurl == null || hdurl.isBlank()) ? url : hdurl;
    }
}
