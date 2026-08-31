package com.arthur.asteroid.alerting.config;

import com.arthur.asteroid.alerting.nasa.NasaDonkiClient;
import com.arthur.asteroid.alerting.nasa.NasaNeoClient;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDate;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the cache actually spares the rate limit, and cannot serve one endpoint's
 * data for another's.
 *
 * <p>An IT rather than a unit test because {@code @Cacheable} is a proxy: calling the
 * client directly, as a unit test would, bypasses the advice entirely and the test
 * would pass whether or not caching worked.
 */
@SpringBootTest(properties = {
        "spring.kafka.admin.auto-create=false",
        "asteroid.nasa.api-key=test-key"
})
class NasaCacheIT {

    private static WireMockServer nasa;

    @Autowired
    private NasaDonkiClient donkiClient;
    @Autowired
    private NasaNeoClient neoClient;
    @Autowired
    private CacheManager cacheManager;

    @BeforeAll
    static void startNasa() {
        nasa = new WireMockServer(options().dynamicPort());
        nasa.start();
    }

    @AfterAll
    static void stopNasa() {
        nasa.stop();
    }

    @DynamicPropertySource
    static void nasaUrl(final DynamicPropertyRegistry registry) {
        registry.add("asteroid.nasa.base-url", nasa::baseUrl);
    }

    @BeforeEach
    void resetEverything() {
        nasa.resetAll();
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @Test
    @DisplayName("a repeated read costs one upstream call, not two")
    void repeatedReadHitsNasaOnce() {
        // The whole reason this exists: DEMO_KEY allows 30 requests an hour across
        // every api.nasa.gov endpoint, and one page load spends several.
        stub("/DONKI/CME", "[]");

        donkiClient.coronalMassEjections(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        donkiClient.coronalMassEjections(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        donkiClient.coronalMassEjections(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        nasa.verify(exactly(1), getRequestedFor(urlPathEqualTo("/DONKI/CME")));
    }

    @Test
    @DisplayName("a different window is a different key")
    void differentArgumentsAreNotShared() {
        stub("/DONKI/CME", "[]");

        donkiClient.coronalMassEjections(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        donkiClient.coronalMassEjections(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        nasa.verify(exactly(2), getRequestedFor(urlPathEqualTo("/DONKI/CME")));
    }

    @Test
    @DisplayName("DONKI's three endpoints never serve each other's data")
    void donkiEndpointsDoNotShareACache() {
        // The bug this guards against is quiet and completely plausible-looking.
        // Spring's SimpleKeyGenerator builds a key from the method ARGUMENTS ONLY -
        // the method is not part of it - and all three DONKI endpoints take the same
        // (LocalDate, LocalDate). Share one cache name between them and a request for
        // solar flares returns the coronal mass ejections already fetched for that
        // window. Hence one cache name per method.
        stub("/DONKI/CME", "[{\"activityID\":\"CME-1\",\"startTime\":\"2026-08-02T10:45Z\"}]");
        stub("/DONKI/GST", "[{\"gstID\":\"GST-1\",\"startTime\":\"2026-08-12T18:00Z\"}]");
        stub("/DONKI/FLR", "[{\"flrID\":\"FLR-1\",\"beginTime\":\"2026-08-11T02:58Z\",\"classType\":\"X2.1\"}]");

        final LocalDate from = LocalDate.of(2026, 8, 1);
        final LocalDate to = LocalDate.of(2026, 8, 31);

        assertThat(donkiClient.coronalMassEjections(from, to))
                .singleElement().extracting(cme -> cme.activityId()).isEqualTo("CME-1");
        assertThat(donkiClient.geomagneticStorms(from, to))
                .singleElement().extracting(gst -> gst.gstId()).isEqualTo("GST-1");
        assertThat(donkiClient.solarFlares(from, to))
                .singleElement().extracting(flr -> flr.flrId()).isEqualTo("FLR-1");

        // each endpoint really was called, rather than one answering for all three
        nasa.verify(exactly(1), getRequestedFor(urlPathEqualTo("/DONKI/CME")));
        nasa.verify(exactly(1), getRequestedFor(urlPathEqualTo("/DONKI/GST")));
        nasa.verify(exactly(1), getRequestedFor(urlPathEqualTo("/DONKI/FLR")));
    }

    @Test
    @DisplayName("the alerting scan is NOT cached, because it publishes events")
    void feedScanIsNeverCached() {
        // lookup and browse are cached; findAsteroids is deliberately not. A cached
        // scan would publish events from a window read minutes ago and report them as
        // current, which defeats the point of scanning.
        stub("/neo/rest/v1/feed", "{\"element_count\":0,\"near_earth_objects\":{}}");

        neoClient.findAsteroids(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 7));
        neoClient.findAsteroids(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 7));

        nasa.verify(exactly(2), getRequestedFor(urlPathEqualTo("/neo/rest/v1/feed")));
    }

    @Test
    @DisplayName("lookup, unlike the scan, is cached")
    void lookupIsCached() {
        // is_potentially_hazardous_asteroid is required, not optional: it maps to a
        // primitive boolean, so a response without it fails to parse rather than
        // defaulting to false. That is deliberate - see Asteroid - and it means a
        // realistic stub has to include it.
        stub("/neo/rest/v1/neo/2000433",
                "{\"id\":\"2000433\",\"name\":\"433 Eros\","
                        + "\"is_potentially_hazardous_asteroid\":false}");

        neoClient.lookup("2000433");
        neoClient.lookup("2000433");

        nasa.verify(exactly(1), getRequestedFor(urlPathEqualTo("/neo/rest/v1/neo/2000433")));
    }

    private static void stub(final String path, final String body) {
        nasa.stubFor(get(urlPathEqualTo(path))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }
}
