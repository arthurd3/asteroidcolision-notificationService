package com.arthur.asteroidcolision.client;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;

@Service
public class NasaClient {

    public List<Asteroid> getNeoAsteroid(final LocalDate fromDate , final LocalDate toDate){
        final RestTemplate restTemplate = new RestTemplate();
    }
}
