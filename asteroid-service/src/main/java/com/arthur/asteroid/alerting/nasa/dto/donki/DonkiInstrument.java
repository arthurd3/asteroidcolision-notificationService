package com.arthur.asteroid.alerting.nasa.dto.donki;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A spacecraft instrument that observed an event, e.g. "SOHO: LASCO/C2".
 *
 * <p>A one-field object rather than a plain string, because that is how DONKI sends
 * it. Flattening it here would mean the record no longer describes the payload.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DonkiInstrument(@JsonProperty("displayName") String displayName) {
}
