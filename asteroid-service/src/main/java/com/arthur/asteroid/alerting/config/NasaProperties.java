package com.arthur.asteroid.alerting.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Settings for every NASA API this service reads.
 *
 * <p>{@code baseUrl} is the API <em>root</em> ({@code https://api.nasa.gov}), not an
 * endpoint. It used to be the full NEO feed URL, which worked while there was exactly
 * one API and stopped working the moment there were four: an endpoint's path is part
 * of that client's contract with NASA, not a deployment setting. Splitting them means
 * a reader of {@code RestNasaApodClient} can see {@code /planetary/apod} at the call
 * site instead of reconstructing it from a yaml key.
 *
 * <p>Timeouts are per API rather than global, because the APIs are not comparable. A
 * DONKI query over a month of space weather routinely takes 60-90 seconds; the NEO
 * feed answers in under two. A single shared {@code spring.http.clients.read-timeout}
 * has to either kill DONKI or let a hung NEO call pin a request thread for a minute
 * and a half. See {@link NasaRestClientsConfig}.
 *
 * <p>Bound and validated at startup, so a missing API key fails the context
 * immediately with a readable message instead of surfacing as a 403 on the first
 * request.
 *
 * @param baseUrl API root, without a trailing slash and without any endpoint path
 * @param apiKey  NASA API key. DEMO_KEY works but is capped at 30 requests per hour
 *                <em>across every api.nasa.gov endpoint</em>, which one page of the
 *                front end can exhaust on its own
 */
@Validated
@ConfigurationProperties(prefix = "asteroid.nasa")
public record NasaProperties(

        @NotBlank String baseUrl,
        @NotBlank String apiKey,

        @NotNull @Valid @DefaultValue Neo neo,
        @NotNull @Valid @DefaultValue Apod apod,
        @NotNull @Valid @DefaultValue Donki donki,
        @NotNull @Valid @DefaultValue Epic epic
) {

    /**
     * Connect and read budget for one upstream.
     *
     * @param connect how long to wait for the TCP connection
     * @param read    how long to wait for the response once connected
     */
    public record Timeouts(
            @NotNull @DefaultValue("3s") Duration connect,
            @NotNull @DefaultValue("10s") Duration read
    ) {
    }

    /**
     * Near-Earth-Object Web Service: the feed, plus lookup and browse.
     *
     * @param lookaheadDays  default scan window. The feed itself rejects spans longer
     *                       than 7 days, which is why the ceiling is not a preference
     * @param browsePageSize default page size for the browse catalogue; NASA caps it at 20
     */
    public record Neo(
            @Min(1) @Max(7) @DefaultValue("7") int lookaheadDays,
            @Min(1) @Max(20) @DefaultValue("20") int browsePageSize,
            @NotNull @Valid @DefaultValue Timeouts timeouts
    ) {
    }

    /** Astronomy Picture of the Day. */
    public record Apod(
            @NotNull @Valid @DefaultValue Timeouts timeouts
    ) {
    }

    /**
     * Space Weather Database Of Notifications, Knowledge, Information.
     *
     * @param defaultWindowDays window used when the caller supplies no dates
     * @param maxWindowDays     refused above this: the response grows without bound and
     *                          so does the time DONKI takes to produce it
     */
    public record Donki(
            @Min(1) @Max(90) @DefaultValue("30") int defaultWindowDays,
            @Min(1) @Max(365) @DefaultValue("90") int maxWindowDays,
            @NotNull @Valid @DefaultValue Timeouts timeouts
    ) {
    }

    /**
     * Earth Polychromatic Imaging Camera.
     *
     * @param imageCacheTtl how long a proxied archive PNG may be cached. The archive is
     *                      immutable once published, so this can be aggressive
     */
    public record Epic(
            @NotNull @Valid @DefaultValue Timeouts timeouts,
            @NotNull @DefaultValue("7d") Duration imageCacheTtl
    ) {
    }
}
