package com.arthur.asteroid.alerting.nasa;

import com.arthur.asteroid.alerting.config.NasaPropertiesFixture;
import com.arthur.asteroid.alerting.nasa.dto.epic.EpicImage;
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
import java.time.LocalDateTime;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/** EPIC's own shape: the non-ISO timestamp and the assembled archive path. */
class RestNasaEpicClientTest {

    private static WireMockServer wireMock;
    private RestNasaEpicClient client;

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
        client = new RestNasaEpicClient(
                RestClient.builder().baseUrl(wireMock.baseUrl()).build(),
                NasaPropertiesFixture.pointingAt(wireMock.baseUrl()));
    }

    @Test
    @DisplayName("parses the space-separated timestamp, which is not ISO-8601")
    void parsesNonIsoTimestamp() throws IOException {
        // "2026-08-29 00:41:06" - a space where ISO-8601 requires a T. Without the
        // explicit @JsonFormat pattern on EpicImage, every single frame fails to parse.
        stubNatural(fixture("nasa/epic-natural.json"));

        final List<EpicImage> images = client.naturalImages(null);

        assertThat(images).hasSize(2);
        assertThat(images.getFirst().date())
                .isEqualTo(LocalDateTime.of(2026, 8, 29, 0, 41, 6));
    }

    @Test
    @DisplayName("parses the frame metadata a view needs")
    void parsesFrameMetadata() throws IOException {
        stubNatural(fixture("nasa/epic-natural.json"));

        assertThat(client.naturalImages(null).getFirst()).satisfies(image -> {
            assertThat(image.identifier()).isEqualTo("20260829004554");
            assertThat(image.image()).isEqualTo("epic_1b_20260829004554");
            assertThat(image.caption()).contains("DSCOVR");
            assertThat(image.centroidCoordinates().lat()).isEqualTo(8.049316);
            assertThat(image.centroidCoordinates().lon()).isEqualTo(175.246582);
        });
    }

    @Test
    @DisplayName("builds the archive path from the date, not from the identifier")
    void buildsArchivePathFromDate() throws IOException {
        // identifier is 20260829004554 while date is 00:41:06 - they disagree by
        // minutes. The archive is keyed by date, so using the identifier's digits to
        // build the path 404s on any frame that crosses midnight UTC.
        stubNatural(fixture("nasa/epic-natural.json"));

        assertThat(client.naturalImages(null).getFirst().archivePath("natural"))
                .isEqualTo("/EPIC/archive/natural/2026/08/29/png/epic_1b_20260829004554.png");
    }

    @Test
    @DisplayName("asks for one day as a path segment, not a query parameter")
    void requestsOneDayByPath() throws IOException {
        wireMock.stubFor(get(urlPathEqualTo("/EPIC/api/natural/date/2026-08-29"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(fixture("nasa/epic-natural.json"))));

        assertThat(client.naturalImages(LocalDate.of(2026, 8, 29))).hasSize(2);
    }

    @Test
    @DisplayName("parses the list of available dates")
    void parsesAvailableDates() {
        wireMock.stubFor(get(urlPathEqualTo(RestNasaEpicClient.AVAILABLE_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[\"2026-08-29\",\"2026-08-28\",\"2026-08-27\"]")));

        assertThat(client.availableNaturalDates())
                .containsExactly(LocalDate.of(2026, 8, 29),
                        LocalDate.of(2026, 8, 28),
                        LocalDate.of(2026, 8, 27));
    }

    @Test
    @DisplayName("fetches the PNG bytes unchanged")
    void fetchesPngBytes() {
        final byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        wireMock.stubFor(get(urlPathEqualTo("/EPIC/archive/natural/2026/08/29/png/epic_1b_20260829004554.png"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "image/png")
                        .withBody(png)));

        assertThat(client.naturalImagePng("/EPIC/archive/natural/2026/08/29/png/epic_1b_20260829004554.png"))
                .isEqualTo(png);
    }

    private static void stubNatural(final String body) {
        wireMock.stubFor(get(urlPathEqualTo(RestNasaEpicClient.NATURAL_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    private static String fixture(final String name) throws IOException {
        try (InputStream in = RestNasaEpicClientTest.class.getClassLoader().getResourceAsStream(name)) {
            assertThat(in).as("fixture %s must be on the test classpath", name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
