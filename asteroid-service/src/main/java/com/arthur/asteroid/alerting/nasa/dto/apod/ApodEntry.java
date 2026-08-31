package com.arthur.asteroid.alerting.nasa.dto.apod;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/**
 * One Astronomy Picture of the Day.
 *
 * <p>The interesting part is {@code mediaType}: APOD publishes video roughly one day
 * a week, and on those days {@code url} is a YouTube or Vimeo embed rather than an
 * image, and {@code hdurl} is absent entirely. A page that renders every entry in an
 * {@code <img>} shows a broken image once a week, so the branch is part of the model
 * rather than something each view rediscovers.
 *
 * <p>Unlike EPIC, APOD's {@code url} and {@code hdurl} point at apod.nasa.gov and
 * carry no API key, so they can be linked straight from a browser with nothing
 * proxied. The contrast between the two is worth knowing: whether a media URL needs
 * a credential is a property of the individual API, not of NASA.
 *
 * @param copyright absent for public-domain images, which is most of them
 * @param hdurl     absent for videos and for some older entries
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApodEntry(

        LocalDate date,
        String title,
        String explanation,
        String url,
        String hdurl,
        String copyright,
        @JsonProperty("media_type") String mediaType,
        @JsonProperty("service_version") String serviceVersion
) {

    private static final String IMAGE = "image";
    private static final String VIDEO = "video";

    public boolean image() {
        return IMAGE.equals(mediaType);
    }

    public boolean video() {
        return VIDEO.equals(mediaType);
    }

    /** Highest-resolution URL available, falling back when {@code hdurl} is absent. */
    public String displayUrl() {
        return (hdurl == null || hdurl.isBlank()) ? url : hdurl;
    }
}
