package com.arthur.asteroid.alerting.nasa;

import com.arthur.asteroid.alerting.config.NasaPropertiesFixture;
import com.arthur.asteroid.alerting.nasa.dto.apod.ApodEntry;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/** APOD's own shape: the media_type branch and the optional hdurl. */
class RestNasaApodClientTest {

    private static WireMockServer wireMock;
    private RestNasaApodClient client;

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
        client = new RestNasaApodClient(
                RestClient.builder().baseUrl(wireMock.baseUrl()).build(),
                NasaPropertiesFixture.pointingAt(wireMock.baseUrl()));
    }

    @Test
    @DisplayName("parses an image entry and prefers the high-resolution URL")
    void parsesImageEntry() {
        stub("""
                {
                  "date": "2026-08-30",
                  "explanation": "A galaxy, seen edge on.",
                  "hdurl": "https://apod.nasa.gov/apod/image/2608/galaxy_hd.jpg",
                  "media_type": "image",
                  "service_version": "v1",
                  "title": "NGC 4565: Galaxy on Edge",
                  "url": "https://apod.nasa.gov/apod/image/2608/galaxy.jpg",
                  "copyright": "Some Astronomer"
                }
                """);

        final ApodEntry entry = client.pictureOfTheDay(LocalDate.of(2026, 8, 30));

        assertThat(entry.date()).isEqualTo(LocalDate.of(2026, 8, 30));
        assertThat(entry.title()).isEqualTo("NGC 4565: Galaxy on Edge");
        assertThat(entry.copyright()).isEqualTo("Some Astronomer");
        assertThat(entry.image()).isTrue();
        assertThat(entry.video()).isFalse();
        assertThat(entry.displayUrl()).endsWith("galaxy_hd.jpg");
    }

    @Test
    @DisplayName("a video entry has no hdurl, so displayUrl falls back to url")
    void parsesVideoEntry() {
        // APOD publishes video roughly one day a week. Rendering that day in an <img>
        // is the bug this branch exists to prevent.
        stub("""
                {
                  "date": "2026-08-24",
                  "explanation": "A launch, filmed from the pad.",
                  "media_type": "video",
                  "service_version": "v1",
                  "title": "Liftoff",
                  "url": "https://www.youtube.com/embed/abc123"
                }
                """);

        final ApodEntry entry = client.pictureOfTheDay(LocalDate.of(2026, 8, 24));

        assertThat(entry.video()).isTrue();
        assertThat(entry.image()).isFalse();
        assertThat(entry.hdurl()).isNull();
        assertThat(entry.displayUrl()).isEqualTo("https://www.youtube.com/embed/abc123");
    }

    @Test
    @DisplayName("a public-domain entry simply has no copyright field")
    void parsesEntryWithoutCopyright() {
        stub("""
                {
                  "date": "2026-08-29", "media_type": "image", "service_version": "v1",
                  "title": "Public Domain", "explanation": "...",
                  "url": "https://apod.nasa.gov/apod/image/2608/pd.jpg"
                }
                """);

        assertThat(client.pictureOfTheDay(LocalDate.of(2026, 8, 29)).copyright()).isNull();
    }

    @Test
    @DisplayName("sends the requested date")
    void sendsRequestedDate() {
        stub("{\"date\":\"2026-08-30\",\"media_type\":\"image\",\"title\":\"t\",\"url\":\"u\"}");

        client.pictureOfTheDay(LocalDate.of(2026, 8, 30));

        wireMock.verify(getRequestedFor(urlPathEqualTo(RestNasaApodClient.PATH))
                .withQueryParam("date", equalTo("2026-08-30"))
                .withQueryParam("api_key", equalTo(NasaPropertiesFixture.API_KEY)));
    }

    @Test
    @DisplayName("omits the date parameter entirely when asking for today")
    void omitsDateForToday() {
        // sending date= with an empty value is a 400 from NASA, not "today"
        stub("{\"date\":\"2026-08-31\",\"media_type\":\"image\",\"title\":\"t\",\"url\":\"u\"}");

        client.pictureOfTheDay(null);

        wireMock.verify(getRequestedFor(urlPathEqualTo(RestNasaApodClient.PATH))
                .withQueryParam("date", absent()));
    }

    private static void stub(final String body) {
        wireMock.stubFor(get(urlPathEqualTo(RestNasaApodClient.PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }
}
