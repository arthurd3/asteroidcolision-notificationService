package com.arthur.asteroid.webui.backend;

import com.arthur.asteroid.webui.backend.dto.AlertSummaryView;
import com.arthur.asteroid.webui.backend.dto.ApodEntryView;
import com.arthur.asteroid.webui.backend.dto.AsteroidView;
import com.arthur.asteroid.webui.backend.dto.EpicImageView;
import com.arthur.asteroid.webui.backend.dto.NeoBrowseView;
import com.arthur.asteroid.webui.backend.dto.NeoFeedView;
import com.arthur.asteroid.webui.backend.dto.SpaceWeatherViews.CoronalMassEjectionView;
import com.arthur.asteroid.webui.backend.dto.SpaceWeatherViews.GeomagneticStormView;
import com.arthur.asteroid.webui.backend.dto.SpaceWeatherViews.SolarFlareView;
import com.arthur.asteroid.webui.config.BackendProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Everything this front end reads from asteroid-service.
 *
 * <p>One class rather than one per NASA API, because from here they are all just
 * paths on the same service with the same failure mode. The per-API distinctions
 * that mattered upstream - separate timeouts, separate circuit breakers - were dealt
 * with there; what reaches this module is either JSON or a problem document.
 */
@Component
public class AsteroidServiceClient {

    private final BackendEndpoint endpoint;

    public AsteroidServiceClient(@Qualifier("asteroidServiceRestClient") RestClient restClient,
                                 BackendProperties properties,
                                 ObjectMapper objectMapper) {
        this.endpoint = new BackendEndpoint(
                restClient, properties.asteroidService().displayName(), objectMapper);
    }

    public String displayName() {
        return endpoint.displayName();
    }

    // ------------------------------------------------------------------ APOD

    public ApodEntryView pictureOfTheDay(final LocalDate date) {
        return endpoint.get("/api/v1/nasa/apod",
                builder -> date == null ? builder : builder.queryParam("date", date),
                new ParameterizedTypeReference<>() {
                });
    }

    // ------------------------------------------------------------------- NEO

    public NeoFeedView neoFeed(final LocalDate from, final LocalDate to) {
        return endpoint.get("/api/v1/nasa/neo/feed",
                builder -> from == null || to == null
                        ? builder
                        : builder.queryParam("from", from).queryParam("to", to),
                new ParameterizedTypeReference<>() {
                });
    }

    public AsteroidView neoLookup(final String id) {
        return endpoint.get("/api/v1/nasa/neo/" + id, UnaryOperator.identity(),
                new ParameterizedTypeReference<>() {
                });
    }

    public NeoBrowseView neoBrowse(final int page, final int size) {
        return endpoint.get("/api/v1/nasa/neo/browse",
                builder -> builder.queryParam("page", page).queryParam("size", size),
                new ParameterizedTypeReference<>() {
                });
    }

    // --------------------------------------------------------- space weather

    public List<CoronalMassEjectionView> coronalMassEjections(final LocalDate from, final LocalDate to) {
        return endpoint.get("/api/v1/nasa/donki/cme", window(from, to),
                new ParameterizedTypeReference<>() {
                });
    }

    public List<GeomagneticStormView> geomagneticStorms(final LocalDate from, final LocalDate to) {
        return endpoint.get("/api/v1/nasa/donki/gst", window(from, to),
                new ParameterizedTypeReference<>() {
                });
    }

    public List<SolarFlareView> solarFlares(final LocalDate from, final LocalDate to) {
        return endpoint.get("/api/v1/nasa/donki/flr", window(from, to),
                new ParameterizedTypeReference<>() {
                });
    }

    // ------------------------------------------------------------------ EPIC

    public List<EpicImageView> epicNatural(final LocalDate date) {
        return endpoint.get("/api/v1/nasa/epic/natural",
                builder -> date == null ? builder : builder.queryParam("date", date),
                new ParameterizedTypeReference<>() {
                });
    }

    public List<LocalDate> epicAvailableDates() {
        return endpoint.get("/api/v1/nasa/epic/natural/dates", UnaryOperator.identity(),
                new ParameterizedTypeReference<>() {
                });
    }

    /**
     * Fetches an EPIC frame's bytes.
     *
     * <p>{@code imagePath} comes from an {@code EpicImageView}, which asteroid-service
     * built and which by construction contains no API key. This is the second of two
     * proxy hops, and it exists so the browser only ever talks to one origin.
     */
    public byte[] epicImage(final String imagePath) {
        return endpoint.getBytes(imagePath);
    }

    // ------------------------------------------------------------------ scan

    /** The only write in the whole front end. */
    public AlertSummaryView triggerScan(final LocalDate from, final LocalDate to) {
        return endpoint.post("/api/v1/asteroid-alerting/alert",
                builder -> from == null || to == null
                        ? builder
                        : builder.queryParam("from", from).queryParam("to", to),
                new ParameterizedTypeReference<>() {
                });
    }

    private static UnaryOperator<org.springframework.web.util.UriBuilder> window(
            final LocalDate from, final LocalDate to) {
        return builder -> from == null || to == null
                ? builder
                : builder.queryParam("from", from).queryParam("to", to);
    }
}
