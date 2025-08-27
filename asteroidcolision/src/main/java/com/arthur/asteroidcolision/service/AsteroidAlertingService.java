package com.arthur.asteroidcolision.service;

import com.arthur.asteroidcolision.client.NasaClient;
import com.arthur.asteroidcolision.dto.Asteroid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class AsteroidAlertingService {

    private final NasaClient nasaClient;

    public AsteroidAlertingService(NasaClient nasaClient) {
        this.nasaClient = nasaClient;
    }

    public void alert(){
        log.info("Alert of Asteroid Alerting");

        final LocalDate fromDate = LocalDate.now();
        final LocalDate toDate = LocalDate.now().plusDays(7);

        log.info("Getting asteroid alerts from {} to {}", fromDate, toDate);
        final List<Asteroid> asteroidList = nasaClient.getNeoAsteroids(fromDate, toDate);
    }
}
