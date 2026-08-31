package com.arthur.asteroid.alerting.nasa;

import com.arthur.asteroid.alerting.config.NasaPropertiesFixture;
import com.arthur.asteroid.alerting.nasa.dto.donki.CoronalMassEjection;
import com.arthur.asteroid.alerting.nasa.dto.donki.GeomagneticStorm;
import com.arthur.asteroid.alerting.nasa.dto.donki.SolarFlare;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * DONKI's own shape, parsed from a recorded response rather than hand-written JSON.
 *
 * <p>{@code src/test/resources/nasa/donki-cme.json} keeps the fields exactly as the
 * live API sent them, seconds-less timestamps and null activeRegionNum included.
 * Writing the JSON by hand from documentation is how a record ends up mapping fields
 * that do not exist.
 */
class RestNasaDonkiClientTest {

    private static WireMockServer wireMock;
    private RestNasaDonkiClient client;

    @BeforeAll
    static void startServer() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopServer() {
        wireMock.stop();
    }

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
        client = new RestNasaDonkiClient(
                RestClient.builder().baseUrl(wireMock.baseUrl()).build(),
                NasaPropertiesFixture.pointingAt(wireMock.baseUrl()));
    }

    @Test
    @DisplayName("parses the recorded CME response")
    void parsesRecordedResponse() throws IOException {
        stub(fixture("nasa/donki-cme.json"));

        final List<CoronalMassEjection> events = client.coronalMassEjections(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertThat(events).hasSize(2);
        assertThat(events.getFirst()).satisfies(cme -> {
            assertThat(cme.activityId()).isEqualTo("2026-08-02T10:45:00-CME-001");
            assertThat(cme.catalog()).isEqualTo("M2M_CATALOG");
            assertThat(cme.note()).contains("jet-like CME");
            assertThat(cme.link()).startsWith("https://webtools.ccmc.gsfc.nasa.gov");
            assertThat(cme.instruments()).singleElement()
                    .extracting(i -> i.displayName()).isEqualTo("GOES: CCOR-1");
        });
    }

    @Test
    @DisplayName("parses DONKI's seconds-less timestamps")
    void parsesSecondsLessTimestamps() throws IOException {
        // "2026-08-02T10:45Z" - a hand-written yyyy-MM-dd'T'HH:mm:ss'Z' pattern would
        // fail on every single record. ISO_OFFSET_DATE_TIME treats seconds as optional.
        stub(fixture("nasa/donki-cme.json"));

        assertThat(client.coronalMassEjections(LocalDate.now(), LocalDate.now()).getFirst().startTime())
                .isEqualTo(OffsetDateTime.parse("2026-08-02T10:45Z"));
    }

    @Test
    @DisplayName("a CME with no identified source region has a null region, not zero")
    void keepsNullActiveRegionNull() throws IOException {
        // boxed on purpose: an int would deserialise null to 0, which reads as
        // "active region zero" rather than "the source was not identified"
        stub(fixture("nasa/donki-cme.json"));

        final List<CoronalMassEjection> events =
                client.coronalMassEjections(LocalDate.now(), LocalDate.now());

        assertThat(events.getFirst().activeRegionNum()).isNull();
        assertThat(events.get(1).activeRegionNum()).isEqualTo(14208);
    }

    @Test
    @DisplayName("an event can list several instruments")
    void parsesMultipleInstruments() throws IOException {
        stub(fixture("nasa/donki-cme.json"));

        assertThat(client.coronalMassEjections(LocalDate.now(), LocalDate.now()).get(1).instruments())
                .extracting(i -> i.displayName())
                .containsExactly("SOHO: LASCO/C2", "STEREO A: SECCHI/COR2");
    }

    @Test
    @DisplayName("a quiet window returns an empty list, not a failure")
    void handlesEmptyResponse() {
        stub("[]");

        assertThat(client.coronalMassEjections(LocalDate.now(), LocalDate.now())).isEmpty();
    }

    @Test
    @DisplayName("sends camelCase startDate and endDate, not NeoWs's snake_case")
    void sendsCamelCaseDateParameters() {
        // DONKI silently ignores a parameter it does not recognise and answers with
        // its own default window, so getting this wrong looks like working code that
        // returns the wrong events.
        stub("[]");

        client.coronalMassEjections(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        wireMock.verify(getRequestedFor(urlPathEqualTo(RestNasaDonkiClient.CME_PATH))
                .withQueryParam("startDate", equalTo("2026-08-01"))
                .withQueryParam("endDate", equalTo("2026-08-31"))
                .withQueryParam("api_key", equalTo(NasaPropertiesFixture.API_KEY)));
    }

    // ------------------------------------------------------------------ GST / FLR
    //
    // Both fixtures are live captures, trimmed to records that keep the awkward cases:
    // a storm with fourteen Kp readings beside one with a single reading, and flares
    // of class C, M and X including one with null linkedEvents.

    @Test
    @DisplayName("parses the geomagnetic-storm capture, including the CMEs that caused it")
    void parsesGeomagneticStorms() throws IOException {
        stubPath(RestNasaDonkiClient.GST_PATH, fixture("nasa/donki-gst.json"));

        final List<GeomagneticStorm> storms =
                client.geomagneticStorms(LocalDate.of(2025, 9, 1), LocalDate.of(2026, 8, 31));

        assertThat(storms).hasSize(2);
        assertThat(storms.getFirst()).satisfies(storm -> {
            assertThat(storm.gstId()).isEqualTo("2026-01-19T18:00:00-GST-001");
            assertThat(storm.startTime()).isEqualTo(OffsetDateTime.parse("2026-01-19T18:00Z"));
            // a storm is tracked as it develops, so one carries many readings
            assertThat(storm.kpIndexReadings()).hasSize(14);
            assertThat(storm.peakKpIndex()).contains(8.67);
            // The relationship worth having: a storm points back at what caused it.
            // Asserted by shape rather than by a literal id - DONKI links a storm to a
            // mix of CME, IPS and MPC activities, and pinning the exact set would make
            // this test about one capture rather than about the mapping.
            assertThat(storm.linkedEvents()).hasSize(3)
                    .extracting(e -> e.activityId())
                    .allSatisfy(id -> assertThat(id).matches("\\d{4}-\\d{2}-\\d{2}T.*-[A-Z]{3}-\\d{3}"))
                    .anySatisfy(id -> assertThat(id).endsWith("-CME-001"));
        });
    }

    @Test
    @DisplayName("a storm can be reported from a single Kp reading")
    void parsesStormWithOneReading() throws IOException {
        stubPath(RestNasaDonkiClient.GST_PATH, fixture("nasa/donki-gst.json"));

        assertThat(client.geomagneticStorms(LocalDate.now(), LocalDate.now()).get(1))
                .satisfies(storm -> {
                    assertThat(storm.kpIndexReadings()).hasSize(1);
                    assertThat(storm.peakKpIndex()).contains(6.0);
                });
    }

    @Test
    @DisplayName("parses the solar-flare capture across classes C, M and X")
    void parsesSolarFlares() throws IOException {
        stubPath(RestNasaDonkiClient.FLR_PATH, fixture("nasa/donki-flr.json"));

        final List<SolarFlare> flares =
                client.solarFlares(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 8, 31));

        assertThat(flares).hasSize(3);
        assertThat(flares).extracting(SolarFlare::classLetter).containsExactly("X", "M", "C");

        assertThat(flares.getFirst()).satisfies(flare -> {
            assertThat(flare.flrId()).isEqualTo("2026-06-03T11:19:00-FLR-001");
            assertThat(flare.classType()).isEqualTo("X1.0");
            assertThat(flare.activeRegionNum()).isEqualTo(14455);
            assertThat(flare.sourceLocation()).isNotBlank();
            // seconds-less timestamps, parsed only because ISO_OFFSET_DATE_TIME
            // treats seconds as optional
            assertThat(flare.beginTime()).isEqualTo(OffsetDateTime.parse("2026-06-03T11:19Z"));
            assertThat(flare.peakTime()).isEqualTo(OffsetDateTime.parse("2026-06-03T11:28Z"));
            assertThat(flare.endTime()).isEqualTo(OffsetDateTime.parse("2026-06-03T11:35Z"));
            assertThat(flare.linkedEvents()).singleElement()
                    .extracting(e -> e.activityId()).isEqualTo("2026-06-03T11:48:00-CME-001");
        });
    }

    @Test
    @DisplayName("linkedEvents is null, not absent, when a flare caused nothing")
    void handlesNullLinkedEvents() throws IOException {
        // DONKI sends "linkedEvents": null rather than omitting the field, so the
        // accessor has to default it or every unlinked flare NPEs in the view
        stubPath(RestNasaDonkiClient.FLR_PATH, fixture("nasa/donki-flr.json"));

        assertThat(client.solarFlares(LocalDate.now(), LocalDate.now()).get(1))
                .satisfies(flare -> {
                    assertThat(flare.classType()).isEqualTo("M1.8");
                    assertThat(flare.linkedEvents()).isEmpty();
                });
    }

    @Test
    @DisplayName("every DONKI endpoint sends the same camelCase window parameters")
    void allEndpointsSendTheSameParameters() {
        stubPath(RestNasaDonkiClient.GST_PATH, "[]");
        stubPath(RestNasaDonkiClient.FLR_PATH, "[]");

        client.geomagneticStorms(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        client.solarFlares(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        for (final String path : List.of(RestNasaDonkiClient.GST_PATH, RestNasaDonkiClient.FLR_PATH)) {
            wireMock.verify(getRequestedFor(urlPathEqualTo(path))
                    .withQueryParam("startDate", equalTo("2026-08-01"))
                    .withQueryParam("endDate", equalTo("2026-08-31")));
        }
    }

    private static void stubPath(final String path, final String body) {
        wireMock.stubFor(get(urlPathEqualTo(path))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    private static void stub(final String body) {
        wireMock.stubFor(get(urlPathEqualTo(RestNasaDonkiClient.CME_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    private static String fixture(final String name) throws IOException {
        try (InputStream in = RestNasaDonkiClientTest.class.getClassLoader().getResourceAsStream(name)) {
            assertThat(in).as("fixture %s must be on the test classpath", name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
