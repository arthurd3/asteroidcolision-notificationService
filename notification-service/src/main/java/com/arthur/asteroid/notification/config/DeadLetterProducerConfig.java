package com.arthur.asteroid.notification.config;

import com.arthur.asteroid.contracts.v1.AsteroidCollisionEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Producer used only for dead-letter publication.
 *
 * <p>A DLT record can carry two very different payloads, which is why an ordinary
 * template does not work here:
 *
 * <ul>
 *   <li>a message that failed <em>deserialization</em> is forwarded as the original
 *       raw {@code byte[]} — the whole point is to preserve bytes nobody could parse;</li>
 *   <li>a message that deserialized fine but blew up in the listener is forwarded
 *       as the deserialized object.</li>
 * </ul>
 *
 * <p>With a plain {@code StringSerializer} the first case fails with
 * {@code Can't convert value of class [B}, the recoverer throws, the offset is
 * never committed, and the container re-reads the same poison message forever —
 * exactly the failure the dead-letter topic exists to prevent.
 * {@link DelegatingByTypeSerializer} picks the serializer per payload type.
 *
 * <p>The bootstrap servers come from {@link KafkaConnectionDetails}, not from
 * {@code KafkaProperties} alone. Boot's own Kafka auto-configuration calls
 * {@code buildProducerProperties()} and then applies the connection details on top,
 * because a connection can be supplied by something other than the yaml - a
 * Testcontainers {@code @ServiceConnection}, Docker Compose support, or a cloud
 * binding. Building a factory from the properties alone silently keeps whatever
 * {@code spring.kafka.bootstrap-servers} says, so in any of those environments this
 * producer points at a broker that is not the one everything else is using, and the
 * only symptom is "Topic asteroid-alert.DLT not present in metadata after 60000 ms"
 * while the consumer is demonstrably connected.
 */
@Configuration(proxyBeanMethods = false)
public class DeadLetterProducerConfig {

    @Bean
    ProducerFactory<Object, Object> deadLetterProducerFactory(KafkaProperties kafkaProperties,
                                                             KafkaConnectionDetails connectionDetails) {
        final Map<String, Object> config = new LinkedHashMap<>(kafkaProperties.buildProducerProperties());
        // the step Boot's own auto-configuration takes and this bean used to miss
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                connectionDetails.getProducer().getBootstrapServers());
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.remove(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG);
        config.remove(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG);

        final Map<Class<?>, Serializer<?>> byType = new LinkedHashMap<>();
        byType.put(byte[].class, new ByteArraySerializer());
        byType.put(String.class, new StringSerializer());
        byType.put(AsteroidCollisionEvent.class, new JacksonJsonSerializer<AsteroidCollisionEvent>());

        return new DefaultKafkaProducerFactory<>(
                config,
                new DelegatingByTypeSerializer(byType),
                new DelegatingByTypeSerializer(byType));
    }

    @Bean
    KafkaTemplate<Object, Object> deadLetterKafkaTemplate(
            ProducerFactory<Object, Object> deadLetterProducerFactory) {
        return new KafkaTemplate<>(deadLetterProducerFactory);
    }
}
