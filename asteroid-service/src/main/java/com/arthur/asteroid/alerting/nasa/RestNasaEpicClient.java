package com.arthur.asteroid.alerting.nasa;

import com.arthur.asteroid.alerting.config.NasaProperties;
import com.arthur.asteroid.alerting.nasa.dto.epic.EpicImage;
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
import java.util.List;

/** {@link NasaEpicClient} over {@code /EPIC/*}. */
@Slf4j
@Component
public class RestNasaEpicClient implements NasaEpicClient {

    static final String RESILIENCE_NAME = "nasaEpic";
    static final String NATURAL_PATH = "/EPIC/api/natural";
    static final String AVAILABLE_PATH = "/EPIC/api/natural/available";

    private final NasaEndpoint endpoint;

    public RestNasaEpicClient(@Qualifier("nasaEpicRestClient") RestClient restClient,
                              NasaProperties properties) {
        this.endpoint = new NasaEndpoint(restClient, properties.apiKey(), "NASA EPIC");
    }

    @Override
    @Cacheable(cacheNames = NasaCacheConfig.EPIC_FRAMES)
    @Retry(name = RESILIENCE_NAME)
    @CircuitBreaker(name = RESILIENCE_NAME)
    public List<EpicImage> naturalImages(final LocalDate date) {
        // A date is a path segment here, not a query parameter: /natural returns the
        // most recent set, /natural/date/2026-08-29 returns one day.
        final String path = date == null ? NATURAL_PATH : NATURAL_PATH + "/date/" + date;
        log.debug("Querying EPIC {}", path);

        final List<EpicImage> images = endpoint.get(path, builder -> builder,
                new ParameterizedTypeReference<>() {
                });

        log.info("EPIC returned {} frame(s) for {}", images.size(), date == null ? "the latest set" : date);
        return images;
    }

    @Override
    @Cacheable(cacheNames = NasaCacheConfig.EPIC_DATES)
    @Retry(name = RESILIENCE_NAME)
    @CircuitBreaker(name = RESILIENCE_NAME)
    public List<LocalDate> availableNaturalDates() {
        return endpoint.get(AVAILABLE_PATH, builder -> builder,
                new ParameterizedTypeReference<>() {
                });
    }

    @Override
    @Cacheable(cacheNames = NasaCacheConfig.EPIC_IMAGE)
    @Retry(name = RESILIENCE_NAME)
    @CircuitBreaker(name = RESILIENCE_NAME)
    public byte[] naturalImagePng(final String archivePath) {
        log.debug("Proxying EPIC image {}", archivePath);
        return endpoint.getBytes(archivePath);
    }
}
