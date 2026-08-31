package com.arthur.asteroid.alerting.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the alert topic instead of relying on broker-side auto-creation.
 *
 * <p>Auto-created topics take the broker's defaults for partition count and
 * replication factor, which is silent and environment-dependent. Declaring them
 * makes the shape explicit and reviewable.
 */
@Configuration(proxyBeanMethods = false)
public class KafkaTopicConfig {

    @Bean
    NewTopic asteroidAlertTopic(AlertingProperties properties) {
        return TopicBuilder.name(properties.topic())
                .partitions(properties.partitions())
                .replicas(properties.replicationFactor())
                .build();
    }
}
