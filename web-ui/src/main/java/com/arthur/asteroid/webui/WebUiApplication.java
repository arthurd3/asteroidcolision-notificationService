package com.arthur.asteroid.webui;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * Server-rendered front end over asteroid-service and notification-service.
 *
 * <p>Extends {@link SpringBootServletInitializer} so the war is also deployable to a
 * standalone Tomcat, where there is no {@code main} and the container drives startup
 * through {@code ServletContainerInitializer} instead. Six lines, and it is what
 * makes the {@code provided} scope on spring-boot-starter-tomcat mean something
 * rather than just being smaller.
 *
 * <p>This module holds no NASA client and no database. It renders what the two
 * backends return, so that "the UI is down" and "the pipeline is down" stay separate
 * failures.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class WebUiApplication extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(WebUiApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(WebUiApplication.class, args);
    }
}
