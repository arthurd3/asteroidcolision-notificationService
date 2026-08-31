package com.arthur.asteroid.alerting.nasa;

import com.arthur.asteroid.alerting.config.NasaProperties;
import com.arthur.asteroid.alerting.nasa.dto.apod.ApodEntry;
import com.arthur.asteroid.alerting.config.NasaCacheConfig;
import org.springframework.cache.annotation.Cacheable;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

/**
 * {@link NasaApodClient} over {@code /planetary/apod}.
 *
 * <p>Everything about transport - the API key, error translation, keeping the URI out
 * of logs - belongs to {@link NasaEndpoint}. What is left here is one path and one
 * optional query parameter, which is the point of the split.
 */
@Slf4j
@Component
public class RestNasaApodClient implements NasaApodClient {

    static final String RESILIENCE_NAME = "nasaApod";
    static final String PATH = "/planetary/apod";

    private final NasaEndpoint endpoint;

    public RestNasaApodClient(@Qualifier("nasaApodRestClient") RestClient restClient,
                              NasaProperties properties) {
        this.endpoint = new NasaEndpoint(restClient, properties.apiKey(), "NASA APOD");
    }

    @Override
    @Cacheable(cacheNames = NasaCacheConfig.APOD)
    @Retry(name = RESILIENCE_NAME)
    @CircuitBreaker(name = RESILIENCE_NAME)
    public ApodEntry pictureOfTheDay(final LocalDate date) {
        log.debug("Querying APOD for {}", date == null ? "today" : date);

        return endpoint.get(PATH,
                // omitting the parameter entirely is what asks for today; sending
                // date= with an empty value is a 400
                builder -> date == null ? builder : builder.queryParam("date", date),
                new ParameterizedTypeReference<>() {
                });
    }
}
