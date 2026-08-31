package com.arthur.asteroid.alerting.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Publishing settings for the alert topic.
 *
 * <p>The topic name used to be a literal repeated in three places across the two
 * services; it now has exactly one definition per service, bound from config.
 */
@Validated
@ConfigurationProperties(prefix = "asteroid.alerting")
public record AlertingProperties(

        @NotBlank String topic,
        @Min(1) int partitions,
        @Min(1) short replicationFactor
) {
}
