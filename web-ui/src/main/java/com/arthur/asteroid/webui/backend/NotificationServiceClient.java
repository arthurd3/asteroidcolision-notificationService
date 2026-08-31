package com.arthur.asteroid.webui.backend;

import com.arthur.asteroid.webui.backend.dto.NotificationViews.DeliveryStatsView;
import com.arthur.asteroid.webui.backend.dto.NotificationViews.NotificationDetailView;
import com.arthur.asteroid.webui.backend.dto.NotificationViews.NotificationSummaryView;
import com.arthur.asteroid.webui.backend.dto.NotificationViews.PageView;
import com.arthur.asteroid.webui.config.BackendProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.function.UnaryOperator;

/** Everything this front end reads from notification-service. */
@Component
public class NotificationServiceClient {

    private final BackendEndpoint endpoint;

    public NotificationServiceClient(@Qualifier("notificationServiceRestClient") RestClient restClient,
                                     BackendProperties properties,
                                     ObjectMapper objectMapper) {
        this.endpoint = new BackendEndpoint(
                restClient, properties.notificationService().displayName(), objectMapper);
    }

    public String displayName() {
        return endpoint.displayName();
    }

    public PageView<NotificationSummaryView> history(final int page, final int size) {
        return endpoint.get("/api/v1/notifications",
                builder -> builder.queryParam("page", page).queryParam("size", size),
                new ParameterizedTypeReference<>() {
                });
    }

    public NotificationDetailView detail(final String eventId) {
        return endpoint.get("/api/v1/notifications/" + eventId, UnaryOperator.identity(),
                new ParameterizedTypeReference<>() {
                });
    }

    public DeliveryStatsView stats() {
        return endpoint.get("/api/v1/notifications/stats", UnaryOperator.identity(),
                new ParameterizedTypeReference<>() {
                });
    }
}
