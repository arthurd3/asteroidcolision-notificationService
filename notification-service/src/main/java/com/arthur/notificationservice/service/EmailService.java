package com.arthur.notificationservice.service;

import com.arthur.notificationservice.service.entity.Notification;
import com.arthur.notificationservice.service.repository.NotificationRepository;
import com.arthur.notificationservice.service.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class EmailService {

    @Value("${email.service.from.email}")
    private String fromEmail;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;


    public EmailService(NotificationRepository notificationRepository, UserRepository userRepository, JavaMailSender mailSender) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.mailSender = mailSender;
    }

    @Async
    public void sendAsteroidAlertEmail(){
        final String text = createEmailText();

        if(text == null){
            log.info("Dont Have Asteroid Alert");
            return;
        }


        final List<String> toEmails = userRepository.findAllEmailsAndNotificationEnabled();

        if(toEmails.isEmpty()){
            log.info("Dont have Users to send Alert Email");
            return;
        }

        toEmails.forEach(toEmail -> sendEmail(toEmail , text));
        log.info("Email sent to: {} users", toEmails.size());

    }

    private void sendEmail(final String toEmail, final String text){
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setFrom(fromEmail);
        message.setSubject("Nasa Asteroid Collision Event");
        message.setText(text);
        mailSender.send(message);
    }

    private String createEmailText(){
        List<Notification> notifications = notificationRepository.findByEmailSent(false);

        if(notifications.isEmpty()){
            return null;
        }

        StringBuilder emailText = new StringBuilder();
        emailText.append("Asteroid Alert: \n");
        emailText.append("======================================");

        notifications.forEach(notification -> {
            emailText.append("Asteroid Name: ").append(notification.getAsteroidName()).append("\n");
            emailText.append("Close Approach Date: ").append(notification.getCloseApproachDate()).append("\n");
            emailText.append("Estimated Diameter Avg Meters: ").append(notification.getEstimatedDiameterAvgMeters()).append("\n");
            emailText.append("Miss Distance Kilometers: ").append(notification.getMissDistanceKilometers()).append("\n");
            emailText.append("======================================");
            notification.setEmailSent(true);
            notificationRepository.save(notification);
        });
        return emailText.toString();
    }
}
