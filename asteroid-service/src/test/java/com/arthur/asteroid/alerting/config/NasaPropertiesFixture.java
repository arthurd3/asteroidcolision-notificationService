package com.arthur.asteroid.alerting.config;

import java.time.Duration;

/**
 * One place that knows how to build a {@link NasaProperties} for a test.
 *
 * <p>{@code NasaProperties} is a record with nested records, so every test that
 * constructs one positionally breaks the moment a new NASA API is added. Before this
 * fixture existed, adding a component meant editing three unrelated test files.
 * Adding the fifth API should mean editing this file and nothing else.
 *
 * <p>Timeouts are deliberately short: a test that has to wait three seconds to prove
 * a connection was refused is a test nobody runs.
 */
public final class NasaPropertiesFixture {

    public static final String API_KEY = "test-key";

    /** Points every API at one stub server, typically {@code wireMock.baseUrl()}. */
    public static NasaProperties pointingAt(final String baseUrl) {
        return new NasaProperties(
                baseUrl,
                API_KEY,
                new NasaProperties.Neo(7, 20, timeouts()),
                new NasaProperties.Apod(timeouts()),
                new NasaProperties.Donki(30, 90, timeouts()),
                new NasaProperties.Epic(timeouts(), Duration.ofDays(7)));
    }

    private static NasaProperties.Timeouts timeouts() {
        return new NasaProperties.Timeouts(Duration.ofSeconds(1), Duration.ofSeconds(2));
    }

    private NasaPropertiesFixture() {
    }
}
