package com.arthur.asteroid.alerting.nasa;

import com.arthur.asteroid.alerting.config.NasaProperties;
import com.arthur.asteroid.alerting.nasa.dto.Asteroid;
import com.arthur.asteroid.alerting.nasa.dto.NasaNeoResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * {@link NasaNeoClient} over the NASA NEO feed.
 *
 * <p>Timeouts come from {@code spring.http.client.*} and apply to the injected
 * builder. The previous implementation constructed a {@code new RestTemplate()}
 * per call, which meant no timeouts at all — a hung endpoint pinned the request
 * thread indefinitely — and a fresh connection pool and converter set every time.
 *
 * <p>{@code @Retry} covers transient 5xx and I/O blips; {@code @CircuitBreaker}
 * stops hammering the feed once it is consistently failing, which matters because
 * DEMO_KEY is rate-limited to 30 requests an hour and answers with 429.
 */
@Slf4j
@Component
public class RestNasaNeoClient implements NasaNeoClient {

    static final String RESILIENCE_NAME = "nasaNeoFeed";

    private final RestClient restClient;
    private final NasaProperties properties;

    public RestNasaNeoClient(RestClient.Builder restClientBuilder, NasaProperties properties) {
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
        this.properties = properties;
    }

    @Override
    @Retry(name = RESILIENCE_NAME)
    @CircuitBreaker(name = RESILIENCE_NAME)
    public List<Asteroid> findAsteroids(final LocalDate from, final LocalDate to) {
        log.debug("Querying NASA NEO feed for {} .. {}", from, to);

        final NasaNeoResponse response;
        try {
            response = restClient.get()
                    .uri(builder -> builder
                            .queryParam("start_date", from)
                            .queryParam("end_date", to)
                            .queryParam("api_key", properties.apiKey())
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, res) -> {
                        // the key travels as a query parameter, so the URI must never
                        // reach a log line or an exception message
                        throw new NasaUnavailableException(
                                "NASA NEO feed returned " + res.getStatusCode());
                    })
                    .body(NasaNeoResponse.class);
        } catch (RestClientException ex) {
            throw new NasaUnavailableException("Could not reach the NASA NEO feed", ex);
        }

        if (response == null) {
            throw new NasaUnavailableException("NASA NEO feed returned an empty body");
        }

        final List<Asteroid> asteroids = response.nearEarthObjects().entrySet().stream()
                .sorted(Comparator.comparing(java.util.Map.Entry::getKey))
                .flatMap(entry -> entry.getValue().stream())
                .toList();

        log.info("NASA NEO feed returned {} objects across {} day(s)",
                asteroids.size(), response.nearEarthObjects().size());
        return asteroids;
    }
}
