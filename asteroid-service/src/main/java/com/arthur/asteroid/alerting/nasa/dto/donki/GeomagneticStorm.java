package com.arthur.asteroid.alerting.nasa.dto.donki;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * A disturbance of Earth's magnetic field, usually caused by an arriving CME.
 *
 * <p>Verified against a live capture: every component below is a field DONKI really
 * sends. {@code submissionTime}, {@code versionId} and {@code sentNotifications} are
 * deliberately unmapped - nothing here displays them, and
 * {@code @JsonIgnoreProperties} makes leaving them out free.
 *
 * <p>{@code linkedEvents} is genuinely nullable rather than merely absent, which is
 * why the accessor below defaults it. A storm with no identified cause is normal.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GeomagneticStorm(

        @JsonProperty("gstID") String gstId,
        @JsonProperty("startTime") OffsetDateTime startTime,
        @JsonProperty("allKpIndex") List<KpIndexReading> kpIndexReadings,
        @JsonProperty("linkedEvents") List<LinkedEvent> linkedEvents,
        String link
) {

    /** Never null, so a view can iterate without a guard. */
    public List<KpIndexReading> kpIndexReadings() {
        return kpIndexReadings == null ? List.of() : kpIndexReadings;
    }

    /** Never null, so a view can iterate without a guard. */
    public List<LinkedEvent> linkedEvents() {
        return linkedEvents == null ? List.of() : linkedEvents;
    }

    /** The strongest reading recorded, which is how storms are usually described. */
    public Optional<Double> peakKpIndex() {
        return kpIndexReadings().stream()
                .map(KpIndexReading::kpIndex)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder());
    }
}
