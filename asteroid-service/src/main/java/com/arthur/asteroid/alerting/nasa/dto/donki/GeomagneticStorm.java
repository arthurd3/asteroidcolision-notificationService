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
 * <p><strong>Unverified shape.</strong> Unlike {@link CoronalMassEjection}, whose
 * fields come from a captured live response, the components below are taken from
 * NASA's DONKI documentation. The API key available when this was written was
 * DEMO_KEY, which is capped at 30 requests an hour across every api.nasa.gov
 * endpoint and was exhausted verifying the other four APIs.
 *
 * <p>What that means in practice: {@code @JsonIgnoreProperties(ignoreUnknown = true)}
 * makes an extra field harmless, but a component whose name does not match what DONKI
 * actually sends will simply deserialise to null, silently. Nothing will throw. The
 * fixture in {@code src/test/resources/nasa/donki-gst.json} is hand-written for the
 * same reason, so the test proves the record parses that fixture and nothing more.
 *
 * <p>To settle it: put a real key in {@code .env}, run
 * {@code curl "https://api.nasa.gov/DONKI/GST?startDate=...&endDate=...&api_key=$NASA_API_KEY"},
 * save the response over the fixture, and fix whatever the test then reports.
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
