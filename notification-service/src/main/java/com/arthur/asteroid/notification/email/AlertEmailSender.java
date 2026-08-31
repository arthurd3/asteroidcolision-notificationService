package com.arthur.asteroid.notification.email;

import com.arthur.asteroid.notification.config.NotificationProperties;
import com.arthur.asteroid.notification.persistence.Notification;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;

/** Renders and sends a single asteroid alert. */
@Slf4j
@Component
public class AlertEmailSender {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final NotificationProperties properties;

    public AlertEmailSender(JavaMailSender mailSender,
                            TemplateEngine templateEngine,
                            NotificationProperties properties) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.properties = properties;
    }

    /**
     * Sends one alert to one address.
     *
     * <p>The body is rendered from a template rather than concatenated into a
     * StringBuilder, which among other things fixes the missing newline that ran
     * each separator line into the next field.
     *
     * @throws MailException on delivery failure, so the caller can record the attempt
     */
    public void send(final String toAddress, final String recipientName, final Notification notification) {
        final Context context = new Context();
        context.setVariable("recipientName", recipientName);
        context.setVariable("notification", notification);

        final String body = templateEngine.process("asteroid-alert", context);

        try {
            final MimeMessage message = mailSender.createMimeMessage();
            final MimeMessageHelper helper =
                    new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.fromAddress());
            helper.setTo(toAddress);
            helper.setSubject("Asteroid alert: " + notification.getAsteroidName());
            helper.setText(body, true);
            mailSender.send(message);
        } catch (MessagingException ex) {
            throw new IllegalStateException("Could not build the alert message", ex);
        }
        log.debug("Sent alert {} to {}", notification.getEventId(), toAddress);
    }
}
