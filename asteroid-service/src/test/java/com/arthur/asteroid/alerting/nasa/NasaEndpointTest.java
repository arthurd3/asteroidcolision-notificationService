package com.arthur.asteroid.alerting.nasa;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.function.UnaryOperator;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The transport and failure-translation rules, tested once for all four NASA clients.
 *
 * <p>These cases used to live in {@code RestNasaNeoClientTest}, where they were about
 * to be copy-pasted three more times. They run against a real socket because a mocked
 * {@code RestClient} cannot show that a 429, a truncated body or a refused connection
 * is translated rather than escaping raw.
 */
class NasaEndpointTest {

    private static final String API_KEY = "test-key";
    private static final String PATH = "/some/nasa/endpoint";

    private static WireMockServer wireMock;
    private NasaEndpoint endpoint;

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
        endpoint = new NasaEndpoint(
                RestClient.builder().baseUrl(wireMock.baseUrl()).build(), API_KEY, "NASA Test API");
    }

    @Test
    @DisplayName("appends the API key so no client has to remember to")
    void appendsApiKey() {
        stub(200, "{\"ok\":true}");

        call();

        wireMock.verify(getRequestedFor(urlPathEqualTo(PATH))
                .withQueryParam("api_key", equalTo(API_KEY)));
    }

    @Test
    @DisplayName("passes the caller's own query parameters through")
    void passesCallerParameters() {
        stub(200, "{\"ok\":true}");

        endpoint.get(PATH, builder -> builder.queryParam("date", "2026-03-04"),
                new ParameterizedTypeReference<Map<String, Object>>() {
                });

        wireMock.verify(getRequestedFor(urlPathEqualTo(PATH))
                .withQueryParam("date", equalTo("2026-03-04"))
                .withQueryParam("api_key", equalTo(API_KEY)));
    }

    @ParameterizedTest(name = "HTTP {0} becomes a NasaUnavailableException naming the status")
    @ValueSource(ints = {400, 403, 429, 500, 503})
    @DisplayName("translates every error status instead of letting it escape raw")
    void translatesErrorStatus(final int status) {
        stub(status, "{\"error\":{\"code\":\"WHATEVER\"}}");

        assertThatThrownBy(this::call)
                .isInstanceOf(NasaUnavailableException.class)
                .hasMessageContaining("NASA Test API")
                .hasMessageContaining(String.valueOf(status));
    }

    @Test
    @DisplayName("translates a body that will not parse")
    void translatesMalformedBody() {
        stub(200, "{ this is not json");

        assertThatThrownBy(this::call).isInstanceOf(NasaUnavailableException.class);
    }

    @Test
    @DisplayName("translates a transport failure rather than surfacing a RestClientException")
    void translatesTransportFailure() {
        // nothing is listening on this port, so the connection is refused outright
        final NasaEndpoint unreachable = new NasaEndpoint(
                RestClient.builder().baseUrl("http://localhost:1").build(), API_KEY, "NASA Test API");

        assertThatThrownBy(() -> unreachable.get(PATH, UnaryOperator.identity(),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }))
                .isInstanceOf(NasaUnavailableException.class)
                .hasMessageContaining("Could not reach NASA Test API");
    }

    @Test
    @DisplayName("never puts the API key in the exception message")
    void doesNotLeakApiKeyOnErrorStatus() {
        stub(403, "forbidden");

        assertThatThrownBy(this::call)
                .isInstanceOf(NasaUnavailableException.class)
                .hasMessageNotContaining(API_KEY);
    }

    @Test
    @DisplayName("never copies the cause's message, which carries the full URI and the key")
    void doesNotLeakApiKeyFromTheCause() {
        // The underlying client puts the request URI - api_key and all - into its own
        // exception message. Keeping it as the cause is fine; copying its text into
        // ours is what would publish the credential, because ApiExceptionHandler
        // renders getMessage() straight into the response body.
        final NasaEndpoint unreachable = new NasaEndpoint(
                RestClient.builder().baseUrl("http://localhost:1").build(), API_KEY, "NASA Test API");

        assertThatThrownBy(() -> unreachable.get(PATH, UnaryOperator.identity(),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }))
                .isInstanceOf(NasaUnavailableException.class)
                .hasMessageNotContaining(API_KEY)
                .satisfies(thrown -> assertThat(thrown.getCause()).isNotNull());
    }

    @Test
    @DisplayName("an empty body is a failure, not a null handed back to the caller")
    void treatsEmptyBodyAsFailure() {
        wireMock.stubFor(get(urlPathEqualTo(PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")));

        assertThatThrownBy(this::call)
                .isInstanceOf(NasaUnavailableException.class)
                .hasMessageContaining("empty body");
    }

    private Map<String, Object> call() {
        return endpoint.get(PATH, UnaryOperator.identity(),
                new ParameterizedTypeReference<>() {
                });
    }

    private static void stub(final int status, final String body) {
        wireMock.stubFor(get(urlPathEqualTo(PATH))
                .willReturn(aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }
}
