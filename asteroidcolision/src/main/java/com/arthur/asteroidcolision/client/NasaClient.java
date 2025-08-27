package com.arthur.asteroidcolision.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;

@Service
public class NasaClient {

    @Value("${nasa.neo.api.url}")
    private String nasaNeoApiUrl;

    @Value("${mynasa.api.key}")
    private String nasaApiKey;

    public List<Asteroid> getNeoAsteroid(final LocalDate fromDate , final LocalDate toDate){
        final RestTemplate restTemplate = new RestTemplate();

        final NasaNeoResponse nasaNeoResponse =
                restTemplate.getForObject(getUrl(fromDate , toDate), NasaNeoResponse.class);
    }

    public String getUrl(final LocalDate fromDate, final LocalDate toDate){
        String apiUrl = UriComponentsBuilder.fromUriString(nasaNeoApiUrl)
                .queryParam("start_date", fromDate)
                .queryParam("end_date", toDate)
                .queryParam("api_key", this.nasaApiKey)
                .toUriString();

        return apiUrl;


    }
}
