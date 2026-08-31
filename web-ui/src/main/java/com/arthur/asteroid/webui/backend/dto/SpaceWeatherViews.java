package com.arthur.asteroid.webui.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The three DONKI event types.
 *
 * <p>Grouped in one file because they are one screen and each is small. Note the
 * storm and flare shapes are unverified upstream - see {@code GeomagneticStorm} in
 * asteroid-service - so a field that arrives null here may mean "DONKI does not send
 * that" rather than "DONKI had nothing to report".
 */
public final class SpaceWeatherViews {

    private SpaceWeatherViews() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InstrumentView(@JsonProperty("displayName") String displayName) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LinkedEventView(@JsonProperty("activityID") String activityId) {
    }

    /** A cloud of plasma thrown off the Sun. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CoronalMassEjectionView(
            @JsonProperty("activityID") String activityId,
            @JsonProperty("startTime") OffsetDateTime startTime,
            List<InstrumentView> instruments,
            String sourceLocation,
            @JsonProperty("activeRegionNum") Integer activeRegionNum,
            String note,
            String link) {

        public List<InstrumentView> instruments() {
            return instruments == null ? List.of() : instruments;
        }
    }

    /** A disturbance of Earth's magnetic field. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GeomagneticStormView(
            @JsonProperty("gstID") String gstId,
            @JsonProperty("startTime") OffsetDateTime startTime,
            @JsonProperty("allKpIndex") List<KpIndexView> kpIndexReadings,
            @JsonProperty("linkedEvents") List<LinkedEventView> linkedEvents,
            String link) {

        public List<KpIndexView> kpIndexReadings() {
            return kpIndexReadings == null ? List.of() : kpIndexReadings;
        }

        public List<LinkedEventView> linkedEvents() {
            return linkedEvents == null ? List.of() : linkedEvents;
        }

        /** Kp runs 0-9; 8 or 9 is where power grids and satellites are affected. */
        public Optional<Double> peakKpIndex() {
            return kpIndexReadings().stream()
                    .map(KpIndexView::kpIndex)
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KpIndexView(OffsetDateTime observedTime, Double kpIndex, String source) {
    }

    /** A sudden brightening on the Sun, classified by X-ray intensity. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SolarFlareView(
            @JsonProperty("flrID") String flrId,
            List<InstrumentView> instruments,
            @JsonProperty("beginTime") OffsetDateTime beginTime,
            @JsonProperty("peakTime") OffsetDateTime peakTime,
            @JsonProperty("endTime") OffsetDateTime endTime,
            @JsonProperty("classType") String classType,
            String sourceLocation,
            @JsonProperty("activeRegionNum") Integer activeRegionNum,
            String note,
            String link) {

        public List<InstrumentView> instruments() {
            return instruments == null ? List.of() : instruments;
        }

        /** The letter alone, which is what a badge is coloured by. */
        public String classLetter() {
            return classType == null || classType.isBlank() ? "?" : classType.substring(0, 1);
        }

        /** Null while a flare was still in progress when DONKI was asked. */
        public boolean inProgress() {
            return endTime == null;
        }
    }
}
