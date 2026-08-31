package com.arthur.asteroid.alerting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AsteroidServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AsteroidServiceApplication.class, args);
    }
}
