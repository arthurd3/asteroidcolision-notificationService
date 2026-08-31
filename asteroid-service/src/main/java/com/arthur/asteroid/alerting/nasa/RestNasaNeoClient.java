package com.arthur.asteroid.alerting.nasa;

import com.arthur.asteroid.alerting.config.NasaProperties;
import com.arthur.asteroid.alerting.nasa.dto.Asteroid;
import com.arthur.asteroid.alerting.nasa.dto.NasaNeoResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * {@link NasaNeoClient} over the NASA Near-Earth-Object Web Service.
 *
 * <p>The injected {@link RestClient} is pre-built by
 * {@link com.arthur.asteroid.alerting.config.NasaRestClientsConfig} with this API's
 * own timeout budget and the api.nasa.gov root as its base URL, so the endpoint path
 * belongs here rather than in configuration. Transport and failure translation belong
 * to {@link NasaEndpoint}; what is left in this class is the part that is actually
 * about NEO data.
 *
 * <p>{@code @Retry} covers transient 5xx and I/O blips; {@code @CircuitBreaker} stops
 * hammering the service once it is consistently failing, which matters because
 * DEMO_KEY is rate-limited to 30 requests an hour and answers with 429.
 */
@Slf4j
@Component
public class RestNasaNeoClient implements NasaNeoClient {

    /**
     * One Resilience4j instance for the whole NeoWs service, not one per method. The
     * feed, lookup and browse are the same upstream behind the same rate-limit
     * bucket: a failing feed genuinely predicts a failing lookup, so they should
     * share a circuit. DONKI gets its own precisely because it does not.
     */
    static final String RESILIENCE_NAME = "nasaNeo";

    static final String FEED_PATH = "/neo/rest/v1/feed";

    private final NasaEndpoint endpoint;

    public RestNasaNeoClient(@Qualifier("nasaNeoRestClient") RestClient restClient,
                             NasaProperties properties) {
        this.endpoint = new NasaEndpoint(restClient, properties.apiKey(), "NASA NEO feed");
    }

    @Override
    @Retry(name = RESILIENCE_NAME)
    @CircuitBreaker(name = RESILIENCE_NAME)
    public List<Asteroid> findAsteroids(final LocalDate from, final LocalDate to) {
        log.debug("Querying NASA NEO feed for {} .. {}", from, to);

        final NasaNeoResponse response = endpoint.get(FEED_PATH,
                builder -> builder
                        .queryParam("start_date", from)
                        .queryParam("end_date", to),
                new ParameterizedTypeReference<>() {
                });

        // the feed keys objects by date; flatten in date order so a caller that only
        // looks at the first result gets the soonest approach rather than whichever
        // key the JSON happened to list first
        final List<Asteroid> asteroids = response.nearEarthObjects().entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .flatMap(entry -> entry.getValue().stream())
                .toList();

        log.info("NASA NEO feed returned {} objects across {} day(s)",
                asteroids.size(), response.nearEarthObjects().size());
        return asteroids;
    }
}
