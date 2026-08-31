package com.arthur.asteroid.notification.messaging;

import com.arthur.asteroid.contracts.v1.AsteroidCollisionEvent;
import com.arthur.asteroid.notification.persistence.DeliveryStatus;
import com.arthur.asteroid.notification.persistence.NotificationDeliveryRepository;
import com.arthur.asteroid.notification.persistence.NotificationRepository;
import com.arthur.asteroid.notification.persistence.SubscriberRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The consume path, end to end, against a real broker and a real database.
 *
 * <p>This was the pipeline's largest coverage gap. The README's central claim - that
 * re-scanning an overlapping window adds no rows and sends no duplicate emails - had
 * no test at all, and neither did the retry-then-dead-letter route that keeps a
 * poison message from blocking its partition. Both are behaviours nothing below the
 * integration level can demonstrate: the first depends on a real unique constraint,
 * the second on a real broker actually redelivering.
 *
 * <p>{@code JavaMailSender} is mocked because SMTP is the one collaborator whose
 * realism buys nothing here - the delivery rows are the observable outcome, and
 * {@code DeliveryDispatchServiceTest} already covers what happens when a send fails.
 */
@SpringBootTest(properties = {
        // The dispatch scheduler would otherwise fire mid-test and move rows out of
        // PENDING while the assertions are looking at them. This test is about the
        // consume path; DeliveryDispatchServiceTest covers the sending side.
        "notification.dispatch-initial-delay=PT1H",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        // Boot builds the mail health contributor from a Map<String, JavaMailSender>,
        // and with the sender replaced by @MockitoBean that map resolves empty, which
        // fails the whole context with "'beans' must not be empty". Nothing here is
        // testing the health endpoint.
        "management.health.mail.enabled=false"
})
@Testcontainers
@DirtiesContext
class AsteroidAlertConsumeIT {

    private static final String TOPIC = "asteroid-alert";
    private static final String DLT = TOPIC + ".DLT";

    @Container
    @ServiceConnection
    // Testcontainers 2.0 dropped the self-typed generic: MySQLContainer<?> no longer compiles
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Container
    @ServiceConnection
    // org.testcontainers.kafka.KafkaContainer is the KRaft-native one. There is also a
    // deprecated org.testcontainers.containers.KafkaContainer and a
    // ConfluentKafkaContainer; this is the one that matches the KRaft broker in
    // docker-compose.yml.
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.2.1");

    @Autowired
    private NotificationRepository notifications;
    @Autowired
    private NotificationDeliveryRepository deliveries;
    @Autowired
    private SubscriberRepository subscribers;

    /** The one collaborator worth faking: nothing here is testing SMTP. */
    @MockitoBean
    private JavaMailSender mailSender;

    /**
     * Declares the topic this service consumes.
     *
     * <p>In production asteroid-service owns that declaration - the producer declares
     * what it produces - and notification-service only declares the .DLT it recovers
     * to. This test plays the producer's part, so it takes that responsibility too.
     *
     * <p>It is not optional. With missing-topics-fatal false, a listener that
     * subscribes before the topic exists does not fail; it simply consumes nothing
     * until a metadata refresh, which defaults to five minutes. The symptom is a test
     * that times out with no error anywhere in the log.
     */
    @TestConfiguration
    static class DeclareSourceTopic {
        @Bean
        NewTopic asteroidAlertTopic() {
            return TopicBuilder.name(TOPIC).partitions(3).replicas(1).build();
        }
    }

    @Test
    @DisplayName("an event becomes a notification and one pending delivery per enabled subscriber")
    void ingestsAndFansOut() {
        final String eventId = UUID.randomUUID().toString();

        publish("2000433", event(eventId, "2000433"));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(notifications.existsByEventId(eventId)).isTrue());

        final long enabled = subscribers.countByNotificationEnabledTrue();
        assertThat(enabled).isPositive();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(deliveries.findViewsByEventId(eventId)).hasSize((int) enabled));

        assertThat(deliveries.findViewsByEventId(eventId))
                .allSatisfy(view -> assertThat(view.status()).isEqualTo(DeliveryStatus.PENDING));
    }

    @Test
    @DisplayName("re-delivering the same event adds no rows and queues no second email")
    void isIdempotentAcrossRedelivery() {
        // The README's central claim. Each scan covers a rolling window, so consecutive
        // scans legitimately re-report the same approach, and Kafka is at-least-once on
        // top of that. Until now nothing tested it.
        final String eventId = UUID.randomUUID().toString();
        final AsteroidCollisionEvent event = event(eventId, "2000433");

        publish("2000433", event);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(notifications.existsByEventId(eventId)).isTrue());

        final long notificationsAfterFirst = notifications.count();
        final int deliveriesAfterFirst = deliveries.findViewsByEventId(eventId).size();

        publish("2000433", event);
        publish("2000433", event);

        // there is no positive signal for "nothing happened", so give the listener
        // time to have processed both before asserting the counts are unchanged
        await().during(Duration.ofSeconds(5)).atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(notifications.count()).isEqualTo(notificationsAfterFirst);
            assertThat(deliveries.findViewsByEventId(eventId)).hasSize(deliveriesAfterFirst);
        });
    }

    @Test
    @DisplayName("a payload that cannot be deserialised lands on the DLT instead of blocking the partition")
    void poisonMessageReachesTheDeadLetterTopic() {
        // A DeserializationException is registered as not-retryable, so this should go
        // straight to asteroid-alert.DLT. Publishing raw bytes is the only way to
        // produce one - and it is also what exercises the DelegatingByTypeSerializer on
        // the dead-letter template, without which the recoverer cannot forward a
        // payload it was never able to deserialise.
        try (var consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<String, byte[]>(
                consumerProperties())) {
            consumer.subscribe(java.util.List.of(DLT));
            // take up the current position before publishing
            consumer.poll(Duration.ofSeconds(1));

            sendRawBytes("{ this is definitely not an AsteroidCollisionEvent".getBytes());

            final var records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(45), 1);

            assertThat(records.count()).isEqualTo(1);
            final ConsumerRecord<String, byte[]> dead = records.iterator().next();
            assertThat(new String(dead.value())).contains("not an AsteroidCollisionEvent");
        }
    }

    /**
     * Publishes exactly the way asteroid-service does.
     *
     * <p>The test builds its own producer rather than borrowing a bean, because the
     * producer is the other service. Configuring it from asteroid-service's own yaml
     * settings - JacksonJsonSerializer with the asteroid-collision.v1 type mapping -
     * means this test also proves the alias contract: the consumer resolves the event
     * from the __TypeId__ header alias, not from a fully-qualified class name, which
     * is what lets either service move its packages.
     */
    private void publish(final String key, final AsteroidCollisionEvent event) {
        final Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);
        config.put("spring.json.add.type.headers", true);
        config.put("spring.json.type.mapping",
                "asteroid-collision.v1:com.arthur.asteroid.contracts.v1.AsteroidCollisionEvent");

        final var factory = new DefaultKafkaProducerFactory<String, AsteroidCollisionEvent>(config);
        try {
            new KafkaTemplate<>(factory).send(TOPIC, key, event).join();
        } finally {
            factory.destroy();
        }
    }

    private void sendRawBytes(final byte[] payload) {
        final Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);

        final var factory = new DefaultKafkaProducerFactory<String, byte[]>(config);
        try {
            new KafkaTemplate<>(factory).send(TOPIC, "poison", payload).join();
        } finally {
            factory.destroy();
        }
    }

    private static Map<String, Object> consumerProperties() {
        final Map<String, Object> config =
                new HashMap<>(KafkaTestUtils.consumerProps(KAFKA.getBootstrapServers(), "dlt-probe", "true"));
        config.put("key.deserializer", StringDeserializer.class);
        config.put("value.deserializer", org.apache.kafka.common.serialization.ByteArrayDeserializer.class);
        config.put("auto.offset.reset", "earliest");
        return config;
    }

    private static AsteroidCollisionEvent event(final String eventId, final String asteroidId) {
        return new AsteroidCollisionEvent(
                eventId,
                Instant.parse("2026-03-01T09:00:00Z"),
                asteroidId,
                "433 Eros",
                LocalDate.of(2026, 3, 4),
                new BigDecimal("54321.5000"),
                200.0);
    }
}
