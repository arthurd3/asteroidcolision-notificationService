package com.arthur.asteroid.alerting.nasa;

import com.arthur.asteroid.alerting.config.NasaProperties;
import com.arthur.asteroid.alerting.config.NasaPropertiesFixture;
import com.arthur.asteroid.alerting.nasa.dto.neo.Asteroid;
import com.arthur.asteroid.alerting.nasa.dto.neo.NeoBrowsePage;
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

    @Test
    @DisplayName("lookup parses the orbit, which the feed never sends")
    void parsesLookupWithOrbitalData() {
        wireMock.stubFor(get(urlPathEqualTo("/neo/rest/v1/neo/2000433"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "2000433",
                                  "neo_reference_id": "2000433",
                                  "name": "433 Eros (A898 PA)",
                                  "designation": "433",
                                  "nasa_jpl_url": "https://ssd.jpl.nasa.gov/tools/sbdb_lookup.html#/?sstr=2000433",
                                  "absolute_magnitude_h": 10.41,
                                  "is_potentially_hazardous_asteroid": false,
                                  "is_sentry_object": false,
                                  "estimated_diameter": {
                                    "meters": { "estimated_diameter_min": 22000.0, "estimated_diameter_max": 49000.0 }
                                  },
                                  "close_approach_data": [
                                    {
                                      "close_approach_date": "1900-06-01",
                                      "close_approach_date_full": "1900-Jun-01 16:40",
                                      "epoch_date_close_approach": -2195882400000,
                                      "relative_velocity": {
                                        "kilometers_per_second": "30.9354328365",
                                        "kilometers_per_hour": "111367.5582113129",
                                        "miles_per_hour": "69199.4697119127"
                                      },
                                      "miss_distance": {
                                        "astronomical": "0.0445495565",
                                        "lunar": "17.3297774785",
                                        "kilometers": "6664518.761844655",
                                        "miles": "4141139.931400039"
                                      },
                                      "orbiting_body": "Merc"
                                    }
                                  ],
                                  "orbital_data": {
                                    "orbit_id": "659",
                                    "first_observation_date": "1893-10-29",
                                    "last_observation_date": "2021-05-13",
                                    "data_arc_in_days": 46582,
                                    "observations_used": 9130,
                                    "orbit_uncertainty": "0",
                                    "minimum_orbit_intersection": ".148662",
                                    "eccentricity": ".2229376240782],",
                                    "semi_major_axis": "1.458098291063",
                                    "inclination": "10.82782330306",
                                    "orbital_period": "643.0654021001488",
                                    "orbit_class": {
                                      "orbit_class_type": "AMO",
                                      "orbit_class_description": "Near-Earth asteroid orbits similar to that of 1221 Amor",
                                      "orbit_class_range": "1.017 AU < q (perihelion) < 1.3 AU"
                                    }
                                  }
                                }
                                """)));

        final Asteroid asteroid = client.lookup("2000433");

        assertThat(asteroid.name()).isEqualTo("433 Eros (A898 PA)");
        assertThat(asteroid.sentryObject()).isFalse();
        assertThat(asteroid.absoluteMagnitudeH()).isEqualTo(10.41);
        assertThat(asteroid.nasaJplUrl()).contains("ssd.jpl.nasa.gov");
        assertThat(asteroid.orbitalData()).isNotNull();
        assertThat(asteroid.orbitalData().orbitClass().type()).isEqualTo("AMO");
        assertThat(asteroid.orbitalData().observationsUsed()).isEqualTo(9130);
        assertThat(asteroid.orbitalData().firstObservationDate())
                .isEqualTo(java.time.LocalDate.of(1893, 10, 29));

        // lookup returns approaches to bodies other than Earth, which is worth
        // surfacing rather than quietly filtering away
        assertThat(asteroid.firstApproach()).hasValueSatisfying(approach -> {
            assertThat(approach.orbitingBody()).isEqualTo("Merc");
            assertThat(approach.closeApproachDateFull()).isEqualTo("1900-Jun-01 16:40");
            assertThat(approach.relativeVelocity().kilometersPerSecond()).isEqualTo("30.9354328365");
            assertThat(approach.missDistance().lunar()).isEqualTo("17.3297774785");
            assertThat(approach.missDistance().kilometersValue())
                    .hasValueSatisfying(km -> assertThat(km).isEqualByComparingTo("6664518.761844655"));
        });
    }

    @Test
    @DisplayName("browse parses the pagination envelope")
    void parsesBrowsePage() {
        wireMock.stubFor(get(urlPathEqualTo(RestNasaNeoClient.BROWSE_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "links": { "next": "http://example.test/next", "self": "http://example.test/self" },
                                  "page": { "size": 20, "total_elements": 62193, "total_pages": 3110, "number": 0 },
                                  "near_earth_objects": [
                                    { "id": "2000433", "name": "433 Eros", "is_potentially_hazardous_asteroid": false }
                                  ]
                                }
                                """)));

        final NeoBrowsePage page = client.browse(0, 20);

        assertThat(page.page().totalElements()).isEqualTo(62193L);
        assertThat(page.page().totalPages()).isEqualTo(3110);
        assertThat(page.page().number()).isZero();
        assertThat(page.nearEarthObjects()).singleElement()
                .extracting(Asteroid::name).isEqualTo("433 Eros");

        wireMock.verify(getRequestedFor(urlPathEqualTo(RestNasaNeoClient.BROWSE_PATH))
                .withQueryParam("page", equalTo("0"))
                .withQueryParam("size", equalTo("20")));
    }

    @Test
    @DisplayName("a browse response with no objects yields an empty list, not null")
    void handlesEmptyBrowsePage() {
        wireMock.stubFor(get(urlPathEqualTo(RestNasaNeoClient.BROWSE_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"page\":{\"size\":20,\"total_elements\":0,\"total_pages\":0,\"number\":0}}")));

        assertThat(client.browse(0, 20).nearEarthObjects()).isEmpty();
    }

    private static void stub(final String body) {
        wireMock.stubFor(get(urlPathEqualTo(RestNasaNeoClient.FEED_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }
}