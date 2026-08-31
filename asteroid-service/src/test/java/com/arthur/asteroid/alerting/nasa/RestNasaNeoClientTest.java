package com.arthur.asteroid.alerting.nasa;

import com.arthur.asteroid.alerting.config.NasaProperties;
import com.arthur.asteroid.alerting.config.NasaPropertiesFixture;
import com.arthur.asteroid.alerting.nasa.dto.Asteroid;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * What this client adds on top of {@link NasaEndpoint}: mapping the feed's JSON onto
 * the DTOs, and flattening its date-keyed map in date order.
 *
 * <p>The transport cases - error statuses, unparseable bodies, refused connections,
 * and the API key never appearing in a message - live in {@link NasaEndpointTest},
 * because they are identical for every NASA client and only need testing once.
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
        // path is unchanged because the path moved into the client, not the base URL.
        final NasaProperties properties = NasaPropertiesFixture.pointingAt(wireMock.baseUrl());
        client = new RestNasaNeoClient(
                RestClient.builder().baseUrl(wireMock.baseUrl()).build(), properties);
    }

    @Test
    @DisplayName("parses the feed and ignores fields the contract does not map")
    void parsesFeed() {
        stub("""
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

        final List<Asteroid> asteroids =
                client.findAsteroids(LocalDate.now(), LocalDate.now().plusDays(1));

        assertThat(asteroids).singleElement().satisfies(asteroid -> {
            assertThat(asteroid.name()).isEqualTo("433 Eros");
            assertThat(asteroid.potentiallyHazardous()).isTrue();
            assertThat(asteroid.averageDiameterMeters()).contains(200.0);
            assertThat(asteroid.firstApproach()).isPresent();
        });
    }

    @Test
    @DisplayName("sends the scan window and the API key as query parameters")
    void sendsWindowAndKey() {
        stub("{\"element_count\": 0, \"near_earth_objects\": {}}");

        client.findAsteroids(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 8));

        wireMock.verify(getRequestedFor(urlPathEqualTo(RestNasaNeoClient.FEED_PATH))
                .withQueryParam("start_date", equalTo("2026-03-01"))
                .withQueryParam("end_date", equalTo("2026-03-08"))
                .withQueryParam("api_key", equalTo(NasaPropertiesFixture.API_KEY)));
    }

    @Test
    @DisplayName("flattens the date-keyed map in date order, not JSON order")
    void flattensInDateOrder() {
        // deliberately listed newest-first, which is what the feed sometimes does
        stub("""
                {
                  "element_count": 2,
                  "near_earth_objects": {
                    "2026-03-06": [ { "id": "2", "name": "Later", "is_potentially_hazardous_asteroid": false } ],
                    "2026-03-04": [ { "id": "1", "name": "Sooner", "is_potentially_hazardous_asteroid": false } ]
                  }
                }
                """);

        assertThat(client.findAsteroids(LocalDate.now(), LocalDate.now().plusDays(3)))
                .extracting(Asteroid::name)
                .containsExactly("Sooner", "Later");
    }

    @Test
    @DisplayName("handles a feed response with no objects at all")
    void handlesEmptyFeed() {
        stub("{\"element_count\": 0, \"near_earth_objects\": {}}");

        assertThat(client.findAsteroids(LocalDate.now(), LocalDate.now())).isEmpty();
    }

    private static void stub(final String body) {
        wireMock.stubFor(get(urlPathEqualTo(RestNasaNeoClient.FEED_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }
}
