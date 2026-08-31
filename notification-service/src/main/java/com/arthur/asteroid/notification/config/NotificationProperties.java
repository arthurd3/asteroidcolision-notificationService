package com.arthur.asteroid.notification.config;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param topic       alert topic to consume; was a literal on the listener annotation
 * @param fromAddress envelope sender for outgoing alerts
 * @param batchSize   deliveries claimed per scheduler tick
 * @param maxAttempts attempts before a delivery is parked as FAILED
 */
@Validated
@ConfigurationProperties(prefix = "notification")
public record NotificationProperties(

        @NotBlank String topic,
        @NotBlank @Email String fromAddress,
        @Min(1) int batchSize,
        @Min(1) int maxAttempts
) {
}
