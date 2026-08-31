package com.arthur.asteroid.alerting.nasa;

import com.arthur.asteroid.alerting.config.NasaProperties;
import com.arthur.asteroid.alerting.nasa.dto.donki.CoronalMassEjection;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;

/**
 * {@link NasaDonkiClient} over {@code /DONKI/*}.
 *
 * <p>The resilience annotations differ from every other NASA client here, and each
 * difference is a consequence of the same measurement - a single query can take a
 * minute and a half:
 *
 * <ul>
 *   <li>{@code nasaDonki} is its own Resilience4j instance, so a DONKI timeout does
 *       not open a circuit that sheds APOD and NEO calls;</li>
 *   <li>that instance retries once rather than twice, because three attempts at 90
 *       seconds is a request no caller is still waiting for;</li>
 *   <li>{@code @Bulkhead} caps concurrent calls at two. Without it, a handful of
 *       impatient page refreshes hold a handful of request threads for minutes and
 *       starve everything else in the service.</li>
 * </ul>
 */
@Slf4j
@Component
public class RestNasaDonkiClient implements NasaDonkiClient {

    static final String RESILIENCE_NAME = "nasaDonki";
    static final String CME_PATH = "/DONKI/CME";

    private final NasaEndpoint endpoint;

    public RestNasaDonkiClient(@Qualifier("nasaDonkiRestClient") RestClient restClient,
                               NasaProperties properties) {
        this.endpoint = new NasaEndpoint(restClient, properties.apiKey(), "NASA DONKI");
    }

    @Override
    @Bulkhead(name = RESILIENCE_NAME)
    @Retry(name = RESILIENCE_NAME)
    @CircuitBreaker(name = RESILIENCE_NAME)
    public List<CoronalMassEjection> coronalMassEjections(final LocalDate from, final LocalDate to) {
        return query(CME_PATH, from, to, new ParameterizedTypeReference<>() {
        });
    }

    /**
     * DONKI's endpoints all take the same two parameters and all answer with a bare
     * JSON array, so the only thing that varies between them is the path and the
     * element type.
     */
    private <T> List<T> query(final String path,
                              final LocalDate from,
                              final LocalDate to,
                              final ParameterizedTypeReference<List<T>> type) {

        log.debug("Querying DONKI {} for {} .. {} (this can take over a minute)", path, from, to);

        final List<T> events = endpoint.get(path,
                builder -> builder
                        // camelCase, unlike NeoWs's snake_case start_date/end_date.
                        // The two APIs simply disagree, and DONKI silently ignores a
                        // parameter it does not recognise, returning its default
                        // window instead of an error.
                        .queryParam("startDate", from)
                        .queryParam("endDate", to),
                type);

        log.info("DONKI {} returned {} event(s) for {} .. {}", path, events.size(), from, to);
        return events;
    }
}
