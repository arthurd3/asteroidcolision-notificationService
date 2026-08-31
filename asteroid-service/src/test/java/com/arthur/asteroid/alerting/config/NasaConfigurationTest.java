package com.arthur.asteroid.alerting.config;

import com.arthur.asteroid.alerting.nasa.NasaUnavailableException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the real context against the real {@code application.yaml}.
 *
 * <p>Everything here is coupling that the compiler cannot see:
 *
 * <ul>
 *   <li>the nested {@code NasaProperties} records bind at all - a record component
 *       that fails to bind produces a startup failure, not a compile error;</li>
 *   <li>the Resilience4j instance names in yaml match the {@code RESILIENCE_NAME}
 *       constants. A mistyped name does not fail: Resilience4j silently creates an
 *       instance from the default config, and the only symptom is a retry policy
 *       quietly not being the one you configured;</li>
 *   <li>{@code retry-exceptions} and {@code record-exceptions} name
 *       {@link NasaUnavailableException} by fully-qualified string. Moving or
 *       renaming that class disables retry and the circuit breaker with no error
 *       anywhere. This test is what turns that into a failing build.</li>
 * </ul>
 */
@SpringBootTest(properties = {
        // This test is about HTTP client and resilience wiring, not messaging. Without
        // this the context spends ~40 seconds watching KafkaAdmin fail to reach a broker
        // that no unit test is going to start.
        "spring.kafka.admin.auto-create=false",
        // pinned rather than inherited, so the assertions do not depend on whether the
        // developer running the test happens to have NASA_API_KEY exported
        "asteroid.nasa.api-key=test-key"
})
class NasaConfigurationTest {

    @Autowired
    private NasaProperties properties;

    @Autowired
    private RetryRegistry retryRegistry;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private BulkheadRegistry bulkheadRegistry;

    @Test
    @DisplayName("base-url is the API root, so each client can own its endpoint path")
    void bindsApiRoot() {
        assertThat(properties.baseUrl()).isEqualTo("https://api.nasa.gov");
        assertThat(properties.apiKey()).isEqualTo("test-key");
    }

    @Test
    @DisplayName("nested per-API records bind, including their timeout budgets")
    void bindsNestedRecords() {
        assertThat(properties.neo().lookaheadDays()).isEqualTo(7);
        assertThat(properties.neo().browsePageSize()).isEqualTo(20);
        assertThat(properties.neo().timeouts().read()).isEqualTo(Duration.ofSeconds(10));

        assertThat(properties.apod().timeouts().read()).isEqualTo(Duration.ofSeconds(10));

        assertThat(properties.donki().defaultWindowDays()).isEqualTo(30);
        assertThat(properties.donki().maxWindowDays()).isEqualTo(90);

        assertThat(properties.epic().imageCacheTtl()).isEqualTo(Duration.ofDays(7));
    }

    @Test
    @DisplayName("DONKI gets its own, much longer read timeout")
    void donkiHasItsOwnTimeout() {
        // The measured reason this whole per-API arrangement exists: a DONKI query
        // times out at 30s and needs roughly 90. Ten seconds would break it outright.
        assertThat(properties.donki().timeouts().read()).isEqualTo(Duration.ofSeconds(120));
        assertThat(properties.donki().timeouts().read())
                .isGreaterThan(properties.neo().timeouts().read());
    }

    @ParameterizedTest(name = "resilience4j instance ''{0}'' is configured for NASA failures")
    @ValueSource(strings = {"nasaNeo", "nasaApod", "nasaDonki", "nasaEpic"})
    @DisplayName("every declared instance records NasaUnavailableException")
    void instancesRecordNasaFailures(final String instance) {
        assertThat(retryRegistry.retry(instance).getRetryConfig()
                .getExceptionPredicate()
                .test(new NasaUnavailableException("boom")))
                .as("retry '%s' must treat NasaUnavailableException as retryable", instance)
                .isTrue();

        assertThat(circuitBreakerRegistry.circuitBreaker(instance).getCircuitBreakerConfig()
                .getRecordExceptionPredicate()
                .test(new NasaUnavailableException("boom")))
                .as("circuit breaker '%s' must count NasaUnavailableException as a failure", instance)
                .isTrue();
    }

    @Test
    @DisplayName("DONKI retries once, not three times, because each attempt can take 90 seconds")
    void donkiRetriesLessThanTheOthers() {
        assertThat(retryRegistry.retry("nasaDonki").getRetryConfig().getMaxAttempts()).isEqualTo(2);
        assertThat(retryRegistry.retry("nasaNeo").getRetryConfig().getMaxAttempts()).isEqualTo(3);
    }

    @Test
    @DisplayName("DONKI is bulkheaded so slow calls cannot occupy every request thread")
    void donkiIsBulkheaded() {
        assertThat(bulkheadRegistry.bulkhead("nasaDonki").getBulkheadConfig()
                .getMaxConcurrentCalls()).isEqualTo(2);
    }

    @Test
    @DisplayName("each NASA API gets its own RestClient bean")
    void publishesOneRestClientPerApi(@Qualifier("nasaNeoRestClient") RestClient neo,
                                      @Qualifier("nasaApodRestClient") RestClient apod,
                                      @Qualifier("nasaDonkiRestClient") RestClient donki,
                                      @Qualifier("nasaEpicRestClient") RestClient epic) {
        // distinct instances: sharing one would mean sharing one timeout budget,
        // which is exactly what this configuration exists to avoid
        assertThat(neo).isNotSameAs(apod).isNotSameAs(donki).isNotSameAs(epic);
        assertThat(apod).isNotSameAs(donki).isNotSameAs(epic);
        assertThat(donki).isNotSameAs(epic);
    }
}
