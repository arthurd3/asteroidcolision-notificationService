package com.arthur.asteroid.alerting.nasa;

import com.arthur.asteroid.alerting.config.NasaPropertiesFixture;
import com.arthur.asteroid.alerting.nasa.dto.donki.CoronalMassEjection;
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
