package com.arthur.asteroid.notification.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares this consumer's dead-letter topic.
 *
 * <p>The consumer owns its own DLT — the producer has no reason to know a
 * consumer failed. Declaring it matters because the broker runs with
 * {@code auto.create.topics.enable=false}: without this bean the error handler
 * detects a poison message correctly but then blocks retrying
 * UNKNOWN_TOPIC_OR_PARTITION forever, so the message is neither processed nor
 * quarantined.
 *
 * <p>Partition count matches the source topic because the recoverer preserves the
 * original partition number.
 */
@Configuration(proxyBeanMethods = false)
public class KafkaTopicConfig {

    public static final String DLT_SUFFIX = ".DLT";

    @Bean
    NewTopic asteroidAlertDeadLetterTopic(NotificationProperties properties) {
        return TopicBuilder.name(properties.topic() + DLT_SUFFIX)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
