package com.arthur.asteroid.alerting.domain;

import com.arthur.asteroid.alerting.config.NasaPropertiesFixture;
import com.arthur.asteroid.alerting.messaging.AsteroidEventPublisher;
import com.arthur.asteroid.alerting.nasa.NasaNeoClient;
import com.arthur.asteroid.alerting.nasa.dto.neo.Asteroid;
import com.arthur.asteroid.alerting.nasa.dto.neo.NeoBrowsePage;
import com.arthur.asteroid.alerting.nasa.dto.neo.NeoFixtures;
import com.arthur.asteroid.contracts.v1.AsteroidCollisionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AsteroidAlertingServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 1);
    private static final Clock FIXED =
            Clock.fixed(Instant.parse("2026-03-01T09:00:00Z"), ZoneOffset.UTC);

    private RecordingPublisher publisher;
    private AsteroidAlertingService service;
    private List<Asteroid> feed;

    @BeforeEach
    void setUp() {
        feed = new ArrayList<>();
        publisher = new RecordingPublisher();
        final NasaNeoClient client = new FeedOnlyClient();
        service = new AsteroidAlertingService(
                client, publisher, NasaPropertiesFixture.pointingAt("http://nasa.test"), FIXED);
    }

    @Test
    @DisplayName("publishes only asteroids NASA flagged as hazardous")
    void publishesOnlyHazardous() {
        feed.add(asteroid("1", "Hazardous One", true));
        feed.add(asteroid("2", "Harmless", false));

        final AlertSummary summary = service.alert();

        assertThat(summary.scanned()).isEqualTo(2);
        assertThat(summary.hazardous()).isEqualTo(1);
        assertThat(summary.published()).isEqualTo(1);
        assertThat(publisher.published).singleElement()
                .extracting(AsteroidCollisionEvent::asteroidName)
                .isEqualTo("Hazardous One");
    }

    @Test
    @DisplayName("skips an asteroid with no approach data instead of failing the whole scan")
    void skipsAsteroidWithoutApproachData() {
        feed.add(NeoFixtures.asteroid("1", "No Approach Data", true, List.of()));
        feed.add(asteroid("2", "Usable", true));

        final AlertSummary summary = service.alert();

        // both counted as hazardous, but only the usable one could become an event
        assertThat(summary.hazardous()).isEqualTo(2);
        assertThat(summary.published()).isEqualTo(1);
        assertThat(publisher.published).singleElement()
                .extracting(AsteroidCollisionEvent::asteroidId).isEqualTo("2");
    }

    @Test
    @DisplayName("survives a null close_approach_data array")
    void survivesNullApproachData() {
        feed.add(NeoFixtures.asteroid("1", "Null Approach", true, null));

        assertThat(service.alert().published()).isZero();
    }

    @Test
    @DisplayName("derives a stable event id, so re-scanning the same window is idempotent downstream")
    void eventIdIsDeterministic() {
        feed.add(asteroid("2000433", "Eros", true));

        service.alert();
        service.alert();

        assertThat(publisher.published).hasSize(2);
        assertThat(publisher.published.get(0).eventId())
                .isEqualTo(publisher.published.get(1).eventId());
    }

    @Test
    @DisplayName("maps the NASA payload onto the contract")
    void mapsFields() {
        feed.add(asteroid("2000433", "433 Eros", true));

        service.alert();

        assertThat(publisher.published).singleElement().satisfies(event -> {
            assertThat(event.asteroidId()).isEqualTo("2000433");
            assertThat(event.asteroidName()).isEqualTo("433 Eros");
            assertThat(event.closeApproachDate()).isEqualTo(LocalDate.of(2026, 3, 4));
            assertThat(event.missDistanceKilometers()).isEqualByComparingTo("54321.5");
            // mean of 100 and 300
            assertThat(event.estimatedDiameterAvgMeters()).isEqualTo(200.0);
            assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-03-01T09:00:00Z"));
        });
    }

    @Test
    @DisplayName("scans today through the configured lookahead")
    void usesConfiguredWindow() {
        final AlertSummary summary = service.alert();

        assertThat(summary.from()).isEqualTo(TODAY);
        assertThat(summary.to()).isEqualTo(TODAY.plusDays(7));
    }

    private static Asteroid asteroid(String id, String name, boolean hazardous) {
        return NeoFixtures.asteroid(id, name, hazardous);
    }

    /**
     * NasaNeoClient gained lookup and browse, so it is no longer a functional
     * interface and the one-line lambda that used to stand in for it no longer
     * compiles. Only the feed drives this service; the other two are not reachable
     * from here, so they say so rather than returning a misleading empty result.
     */
    private final class FeedOnlyClient implements NasaNeoClient {

        @Override
        public List<Asteroid> findAsteroids(LocalDate from, LocalDate to) {
            return feed;
        }

        @Override
        public Asteroid lookup(String neoReferenceId) {
            throw new UnsupportedOperationException("alerting never looks an object up");
        }

        @Override
        public NeoBrowsePage browse(int page, int size) {
            throw new UnsupportedOperationException("alerting never browses the catalogue");
        }
    }

    /** Captures what was published without needing a broker or a mocking framework. */
    private static final class RecordingPublisher extends AsteroidEventPublisher {
        private final List<AsteroidCollisionEvent> published = new ArrayList<>();

        private RecordingPublisher() {
            super(null, null);
        }

        @Override
        public int publishAll(List<AsteroidCollisionEvent> events) {
            published.addAll(events);
            return events.size();
        }
    }
}
