package com.arthur.asteroid.alerting.nasa;

import com.arthur.asteroid.alerting.config.NasaProperties;
import com.arthur.asteroid.alerting.config.NasaPropertiesFixture;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.arthur.asteroid.alerting.nasa.dto.Asteroid;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the client against a real socket. A mock RestClient could not show
 * that a 429 or a truncated body is translated instead of escaping raw.
 */
class RestNasaNeoClientTest {

    private static WireMockServer wireMock;
    private RestNasaNeoClient client;

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
        // The client no longer builds its own RestClient - NasaRestClientsConfig does,
        // with this API's timeout budget - so the test supplies the built one. The stub
        // path is unchanged because the path moved into the client, not into the base URL.
        final NasaProperties properties = NasaPropertiesFixture.pointingAt(wireMock.baseUrl());
        client = new RestNasaNeoClient(
                RestClient.builder().baseUrl(wireMock.baseUrl()).build(), properties);
    }

    @Test
    @DisplayName("parses the feed and ignores fields the contract does not map")
    void parsesFeed() {
        stub(200, """
                {
                  "links": { "next": "http://example.test/next", "self": "http://example.test/self" },
                  "element_count": 1,
                  "near_earth_objects": {
                    "2026-03-04": [
                      {
                        "id": "2000433",
                        "neo_reference_id": "2000433",
                        "name": "433 Eros",
                        "absolute_magnitude_h": 10.4,
                        "estimated_diameter": {
                          "meters": { "estimated_diameter_min": 100.0, "estimated_diameter_max": 300.0 }
                        },
                        "is_potentially_hazardous_asteroid": true,
                        "close_approach_data": [
                          {
                            "close_approach_date": "2026-03-04",
                            "miss_distance": { "kilometers": "54321.5", "lunar": "0.14" }
                          }
                        ]
                      }
                    ]
                  }
                }
                """);

        final List<Asteroid> asteroids = client.findAsteroids(LocalDate.now(), LocalDate.now().plusDays(1));

        assertThat(asteroids).singleElement().satisfies(asteroid -> {
            assertThat(asteroid.name()).isEqualTo("433 Eros");
            assertThat(asteroid.potentiallyHazardous()).isTrue();
            assertThat(asteroid.averageDiameterMeters()).contains(200.0);
            assertThat(asteroid.firstApproach()).isPresent();
        });
    }

    @Test
    @DisplayName("translates the rate-limit response instead of letting it escape raw")
    void translatesRateLimit() {
        stub(429, "{\"error\":{\"code\":\"OVER_RATE_LIMIT\"}}");

        assertThatThrownBy(() -> client.findAsteroids(LocalDate.now(), LocalDate.now()))
                .isInstanceOf(NasaUnavailableException.class)
                .hasMessageContaining("429");
    }

    @Test
    @DisplayName("translates a server error")
    void translatesServerError() {
        stub(500, "boom");

        assertThatThrownBy(() -> client.findAsteroids(LocalDate.now(), LocalDate.now()))
                .isInstanceOf(NasaUnavailableException.class);
    }

    @Test
    @DisplayName("translates an unparseable body")
    void translatesMalformedBody() {
        stub(200, "{ this is not json");

        assertThatThrownBy(() -> client.findAsteroids(LocalDate.now(), LocalDate.now()))
                .isInstanceOf(NasaUnavailableException.class);
    }

    @Test
    @DisplayName("handles a feed response with no objects at all")
    void handlesEmptyFeed() {
        stub(200, "{\"element_count\": 0, \"near_earth_objects\": {}}");

        assertThat(client.findAsteroids(LocalDate.now(), LocalDate.now())).isEmpty();
    }

    @Test
    @DisplayName("never puts the API key in the exception message")
    void doesNotLeakApiKeyOnFailure() {
        stub(403, "forbidden");

        assertThatThrownBy(() -> client.findAsteroids(LocalDate.now(), LocalDate.now()))
                .isInstanceOf(NasaUnavailableException.class)
                .hasMessageNotContaining("test-key");
    }

    private static void stub(int status, String body) {
        wireMock.stubFor(get(urlPathEqualTo("/neo/rest/v1/feed"))
                .willReturn(aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }
}
