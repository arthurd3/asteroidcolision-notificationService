package com.arthur.asteroid.notification.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.messaging.converter.MessageConversionException;

/**
 * Retry and dead-letter policy for the alert listener.
 *
 * <p>There was no error handler at all before. A message whose payload could not
 * be deserialized was rejected before the listener ran and, with a bare
 * JsonDeserializer, the container retried the same offset forever — one poison
 * message stopped the pipeline permanently. Nothing was ever captured for replay.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class KafkaErrorHandlingConfig {

    /**
     * Boot's Kafka auto-configuration picks up a single {@code CommonErrorHandler}
     * bean and installs it on the listener container factory, so no factory
     * override is needed.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> deadLetterKafkaTemplate) {
        final DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                deadLetterKafkaTemplate,
                (record, exception) -> {
                    log.error("Routing offset {} of {}-{} to the dead-letter topic",
                            record.offset(), record.topic(), record.partition(), exception);
                    return new TopicPartition(record.topic() + KafkaTopicConfig.DLT_SUFFIX, record.partition());
                });

        final ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(4);
        backOff.setInitialInterval(500L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(10_000L);

        final DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        // a malformed payload will never parse, so retrying it just delays the DLT
        handler.addNotRetryableExceptions(
                DeserializationException.class,
                MessageConversionException.class);
        return handler;
    }
}
