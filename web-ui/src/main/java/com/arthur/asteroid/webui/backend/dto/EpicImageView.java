package com.arthur.asteroid.webui.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

/**
 * One EPIC frame.
 *
 * <p>Like its counterpart in asteroid-service, this record has no field capable of
 * holding a NASA URL. {@code imagePath} is a path on asteroid-service, which web-ui
 * proxies again so the browser only ever talks to one origin. The API key exists in
 * neither hop's output.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EpicImageView(

        String identifier,
        String caption,
        String image,
        LocalDateTime date,
        CoordinatesView centroidCoordinates,
        String imagePath
) {

    /** asteroid-service's own prefix for these paths. */
    private static final String BACKEND_PREFIX = "/api/v1/nasa/epic/image/";

    /** web-ui's equivalent, so the browser talks to one origin only. */
    private static final String PROXY_PREFIX = "/epic/image/";

    /**
     * The same frame, addressed on this service instead of on asteroid-service.
     *
     * <p>A prefix swap and nothing more: the collection/date/name segments are
     * carried through untouched, so whatever asteroid-service validated on the way
     * out is what comes back in. Returns empty if the backend ever sends a path in a
     * shape this does not recognise, which renders as a broken image rather than as
     * a request to somewhere unintended.
     */
    public String proxyPath() {
        if (imagePath == null || !imagePath.startsWith(BACKEND_PREFIX)) {
            return "";
        }
        return PROXY_PREFIX + imagePath.substring(BACKEND_PREFIX.length());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CoordinatesView(double lat, double lon) {
    }
}
