package com.arthur.asteroid.webui.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Where the two backends live and how long to wait for each.
 *
 * <p>Same nested-record shape as asteroid-service's {@code NasaProperties}, and for
 * the same reason: the two backends do not have comparable latency. Anything that
 * goes through asteroid-service to DONKI can legitimately take two minutes, while
 * notification-service is reading its own database and should answer in
 * milliseconds. One shared read timeout would have to be wrong for one of them.
 *
 * @param pageSize default page size for the paginated views
 */
@Validated
@ConfigurationProperties(prefix = "webui")
public record BackendProperties(

        @NotNull @Valid @DefaultValue Backend asteroidService,
        @NotNull @Valid @DefaultValue Backend notificationService,
        @Min(1) @DefaultValue("20") int pageSize
) {

    /**
     * @param displayName what the error page calls this service. Users cannot act on
     *                    "the upstream failed"; they can act on
     *                    "asteroid-service (8080) is not responding"
     */
    public record Backend(
            @NotBlank @DefaultValue("http://localhost:8080") String baseUrl,
            @NotBlank @DefaultValue("backend") String displayName,
            @NotNull @DefaultValue("3s") Duration connectTimeout,
            @NotNull @DefaultValue("10s") Duration readTimeout
    ) {
    }
}
