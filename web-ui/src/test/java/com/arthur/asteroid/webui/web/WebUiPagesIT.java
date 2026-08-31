package com.arthur.asteroid.webui.web;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Renders every page against stubbed backends.
 *
 * <p><strong>This is the only test that compiles the JSPs.</strong> {@code @WebMvcTest}
 * cannot: there is no servlet container in a slice, so MockMvc reports the forward to
 * {@code /WEB-INF/jsp/x.jsp} and stops. A broken taglib URI, a typo in an EL
 * expression, a method that does not exist on a view model, a missing {@code .jspf} -
 * none of it fails until Jasper actually translates the page, which only happens when
 * a real container serves a real request.
 *
 * <p>Failsafe runs this with the module directory as its working directory, which is
 * what lets Boot's {@code DocumentRoot} find {@code src/main/webapp}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebUiPagesIT {

    private static WireMockServer asteroidService;
    private static WireMockServer notificationService;

    @LocalServerPort
    private int port;

    @Autowired
    private RestClient.Builder restClientBuilder;

    private RestClient browser;

    @BeforeAll
    static void startBackends() {
        asteroidService = new WireMockServer(options().dynamicPort());
        notificationService = new WireMockServer(options().dynamicPort());
        asteroidService.start();
        notificationService.start();
    }

    @AfterAll
    static void stopBackends() {
        asteroidService.stop();
        notificationService.stop();
    }

    @DynamicPropertySource
    static void backendUrls(final DynamicPropertyRegistry registry) {
        registry.add("webui.asteroid-service.base-url", asteroidService::baseUrl);
        registry.add("webui.notification-service.base-url", notificationService::baseUrl);
    }

    @BeforeEach
    void stubBackends() {
        asteroidService.resetAll();
        notificationService.resetAll();
        browser = restClientBuilder.clone().baseUrl("http://localhost:" + port).build();

        json(asteroidService, "/api/v1/nasa/apod", """
                {
                  "date": "2026-08-30", "title": "NGC 4565: Galaxy on Edge",
                  "explanation": "A galaxy, seen edge on.",
                  "url": "https://apod.nasa.gov/apod/image/2608/g.jpg",
                  "hdurl": "https://apod.nasa.gov/apod/image/2608/g_hd.jpg",
                  "copyright": "Some Astronomer", "media_type": "image", "service_version": "v1"
                }
                """);

        json(asteroidService, "/api/v1/nasa/neo/feed", """
                {
                  "from": "2026-08-31", "to": "2026-09-07", "elementCount": 1,
                  "objects": [ %s ]
                }
                """.formatted(asteroidJson()));

        json(asteroidService, "/api/v1/nasa/neo/browse", """
                {
                  "near_earth_objects": [ %s ],
                  "page": { "size": 20, "total_elements": 62193, "total_pages": 3110, "number": 0 }
                }
                """.formatted(asteroidJson()));

        json(asteroidService, "/api/v1/nasa/neo/2000433", asteroidJson());

        json(asteroidService, "/api/v1/nasa/donki/cme", """
                [ { "activityID": "2026-08-02T10:45:00-CME-001", "startTime": "2026-08-02T10:45Z",
                    "instruments": [ { "displayName": "GOES: CCOR-1" } ],
                    "sourceLocation": "", "activeRegionNum": null, "note": "A faint CME.",
                    "link": "https://webtools.ccmc.gsfc.nasa.gov/DONKI/view/CME/1/-1" } ]
                """);

        json(asteroidService, "/api/v1/nasa/donki/gst", """
                [ { "gstID": "2026-08-12T18:00:00-GST-001", "startTime": "2026-08-12T18:00Z",
                    "allKpIndex": [ { "observedTime": "2026-08-13T00:00Z", "kpIndex": 7.33, "source": "NOAA" } ],
                    "linkedEvents": [ { "activityID": "2026-08-11T03:36:00-CME-001" } ],
                    "link": "https://webtools.ccmc.gsfc.nasa.gov/DONKI/view/GST/1/-1" } ]
                """);

        json(asteroidService, "/api/v1/nasa/donki/flr", """
                [ { "flrID": "2026-08-11T02:58:00-FLR-001",
                    "instruments": [ { "displayName": "GOES-P: EXIS 1.0-8.0" } ],
                    "beginTime": "2026-08-11T02:58Z", "peakTime": "2026-08-11T03:14Z",
                    "endTime": null, "classType": "X2.1", "sourceLocation": "S08W12",
                    "activeRegionNum": null, "note": "",
                    "link": "https://webtools.ccmc.gsfc.nasa.gov/DONKI/view/FLR/1/-1" } ]
                """);

        json(asteroidService, "/api/v1/nasa/epic/natural", """
                [ { "identifier": "20260829004554", "caption": "Taken by EPIC aboard DSCOVR.",
                    "image": "epic_1b_20260829004554", "date": "2026-08-29T00:41:06",
                    "centroidCoordinates": { "lat": 8.049316, "lon": 175.246582 },
                    "imagePath": "/api/v1/nasa/epic/image/natural/2026/08/29/epic_1b_20260829004554" } ]
                """);

        json(asteroidService, "/api/v1/nasa/epic/natural/dates", "[\"2026-08-29\",\"2026-08-28\"]");

        asteroidService.stubFor(post(urlPathEqualTo("/api/v1/asteroid-alerting/alert"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"from":"2026-08-31","to":"2026-09-07","scanned":46,"hazardous":4,"published":4}
                                """)));

        json(notificationService, "/api/v1/notifications", """
                {
                  "content": [ {
                    "eventId": "1e2d3c4b-0000-0000-0000-000000000001",
                    "asteroidId": "2000433", "asteroidName": "433 Eros",
                    "closeApproachDate": "2026-03-04", "missDistanceKilometers": 54321.5,
                    "estimatedDiameterAvgMeters": 200.0,
                    "occurredAt": "2026-03-01T09:00:00Z", "createdAt": "2026-03-01T09:00:01Z",
                    "pending": 0, "sent": 2, "failed": 1
                  } ],
                  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
                }
                """);

        json(notificationService, "/api/v1/notifications/stats", """
                {"notifications":12,"enabledSubscribers":3,"pending":1,"sent":34,"failed":2,
                 "lastIngestedAt":"2026-03-01T09:00:01Z"}
                """);

        json(notificationService, "/api/v1/notifications/1e2d3c4b-0000-0000-0000-000000000001", """
                {
                  "eventId": "1e2d3c4b-0000-0000-0000-000000000001",
                  "asteroidId": "2000433", "asteroidName": "433 Eros",
                  "closeApproachDate": "2026-03-04", "missDistanceKilometers": 54321.5,
                  "estimatedDiameterAvgMeters": 200.0,
                  "occurredAt": "2026-03-01T09:00:00Z", "createdAt": "2026-03-01T09:00:01Z",
                  "deliveries": [
                    { "recipientEmail": "dev@asteroid.local", "recipientName": "Asteroid Watch (dev)",
                      "status": "SENT", "attempts": 1, "sentAt": "2026-03-01T09:00:30Z",
                      "lastError": null, "createdAt": "2026-03-01T09:00:01Z" },
                    { "recipientEmail": "broken@asteroid.local", "recipientName": "Broken Mailbox",
                      "status": "FAILED", "attempts": 3, "sentAt": null,
                      "lastError": "550 mailbox unavailable", "createdAt": "2026-03-01T09:00:01Z" }
                  ]
                }
                """);
    }

    @ParameterizedTest(name = "GET {0} renders")
    @CsvSource({
            "/,                                                    Overview",
            "/apod,                                                NGC 4565",
            "/neo,                                                 Near-Earth objects",
            "/neo/2000433,                                         Close approaches",
            "/neo/browse,                                          The catalogue",
            "/space-weather?type=cme,                              Coronal mass ejections",
            "/space-weather?type=gst,                              Geomagnetic storms",
            "/space-weather?type=flr,                              Solar flares",
            "/epic,                                                Earth imagery",
            "/scan,                                                Run a scan",
            "/history,                                             Alert history",
            "/history/1e2d3c4b-0000-0000-0000-000000000001,        Recipients"
    })
    @DisplayName("every page compiles and renders")
    void everyPageRenders(final String path, final String marker) {
        final ResponseEntity<String> response = browser.get().uri(path).retrieve().toEntity(String.class);

        assertThat(response.getStatusCode().value()).as("status for %s", path).isEqualTo(200);
        assertThat(response.getHeaders().getContentType())
                .as("content type for %s", path)
                .isNotNull()
                .satisfies(type -> assertThat(MediaType.TEXT_HTML.isCompatibleWith(type)).isTrue());
        assertThat(response.getBody()).as("body of %s", path).contains(marker);
    }

    @Test
    @DisplayName("the shared layout is on every page")
    void sharedLayoutRenders() {
        final String body = browser.get().uri("/").retrieve().body(String.class);

        assertThat(body)
                .contains("<!DOCTYPE html>")
                .contains("/css/app.css")
                .contains("Asteroid Watch")
                // the entity form, because a literal UTF-8 char in a .jspf is mojibake
                .contains("&#9678;");
    }

    @Test
    @DisplayName("dynamic text is HTML-escaped, because ${} in JSP is not")
    void escapesUntrustedText() {
        // The single most important assertion here. Unlike Thymeleaf's th:text, JSP's
        // ${...} writes raw. Untrusted text reaches these pages from APOD explanations,
        // DONKI notes, EPIC captions and notification_delivery.last_error - which
        // contains whatever an SMTP server said.
        json(asteroidService, "/api/v1/nasa/apod", """
                {
                  "date": "2026-08-30", "title": "<script>alert(1)</script>",
                  "explanation": "<img src=x onerror=alert(2)>",
                  "url": "https://apod.nasa.gov/apod/image/2608/g.jpg",
                  "media_type": "image", "service_version": "v1"
                }
                """);

        final String body = browser.get().uri("/apod").retrieve().body(String.class);

        assertThat(body)
                .contains("&lt;script&gt;")
                .doesNotContain("<script>alert(1)</script>")
                .doesNotContain("<img src=x onerror=");
    }

    @Test
    @DisplayName("the error page is reached, not Boot's whitelabel")
    void rendersTheErrorPage() {
        // Two separate Boot behaviours have to be right for this: the whitelabel must
        // be disabled via spring.web.error.* (server.error.* is deprecated at level
        // "error" and binds to nothing), and the view name has to resolve to the JSP
        // rather than to the View bean named "error".
        notificationService.stubFor(get(urlPathMatching("/api/v1/notifications.*"))
                .willReturn(aResponse().withStatus(503)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("""
                                {"type":"https://asteroid.arthur.com/problems/upstream-unavailable",
                                 "title":"Upstream unavailable",
                                 "detail":"The NASA feed is rate-limiting this service.","status":503}
                                """)));

        final ResponseEntity<String> response = browser.get().uri("/history")
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, res) -> { })
                .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody())
                .contains("notification-service")
                // the backend's own RFC 9457 title and detail, rendered by our page
                .contains("Upstream unavailable")
                .contains("The NASA feed is rate-limiting this service.")
                .contains("./mvnw -pl notification-service spring-boot:run")
                // Boot's whitelabel would say this instead
                .doesNotContain("There was an unexpected error");
    }

    @Test
    @DisplayName("no page leaks a NASA API key or archive URL")
    void neverLeaksTheApiKey() {
        final String body = browser.get().uri("/epic").retrieve().body(String.class);

        assertThat(body)
                .doesNotContain("api_key")
                .doesNotContain("api.nasa.gov/EPIC/archive")
                // the frame is addressed on this service instead
                .contains("/epic/image/natural/2026/08/29/epic_1b_20260829004554");
    }

    private static String asteroidJson() {
        return """
                {
                  "id": "2000433", "neo_reference_id": "2000433", "name": "433 Eros",
                  "designation": "433",
                  "nasa_jpl_url": "https://ssd.jpl.nasa.gov/tools/sbdb_lookup.html#/?sstr=2000433",
                  "absolute_magnitude_h": 10.41,
                  "estimated_diameter": { "meters": {
                      "estimated_diameter_min": 100.0, "estimated_diameter_max": 300.0 } },
                  "is_potentially_hazardous_asteroid": true, "is_sentry_object": false,
                  "close_approach_data": [ {
                      "close_approach_date": "2026-03-04",
                      "close_approach_date_full": "2026-Mar-04 16:40",
                      "relative_velocity": { "kilometers_per_second": "30.9354328365",
                                             "kilometers_per_hour": "111367.5582113129" },
                      "miss_distance": { "astronomical": "0.0445495565", "lunar": "17.3297774785",
                                         "kilometers": "6664518.761844655", "miles": "4141139.9314" },
                      "orbiting_body": "Earth" } ],
                  "orbital_data": {
                      "orbit_id": "659", "first_observation_date": "1893-10-29",
                      "last_observation_date": "2021-05-13", "observations_used": 9130,
                      "orbit_uncertainty": "0", "minimum_orbit_intersection": ".148662",
                      "eccentricity": ".2229376", "semi_major_axis": "1.458098291063",
                      "inclination": "10.82782330306", "orbital_period": "643.0654021",
                      "perihelion_distance": "1.133", "aphelion_distance": "1.783",
                      "orbit_class": { "orbit_class_type": "AMO",
                          "orbit_class_description": "Near-Earth asteroid orbits similar to that of 1221 Amor",
                          "orbit_class_range": "1.017 AU < q (perihelion) < 1.3 AU" } }
                }
                """;
    }

    private static void json(final WireMockServer server, final String path, final String body) {
        server.stubFor(get(urlPathEqualTo(path))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }
}
