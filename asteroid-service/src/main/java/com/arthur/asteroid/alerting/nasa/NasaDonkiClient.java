package com.arthur.asteroid.alerting.nasa;

import com.arthur.asteroid.alerting.nasa.dto.donki.CoronalMassEjection;
import com.arthur.asteroid.alerting.nasa.dto.donki.GeomagneticStorm;
import com.arthur.asteroid.alerting.nasa.dto.donki.SolarFlare;

import java.time.LocalDate;
import java.util.List;

/**
 * Reads NASA's DONKI space-weather database.
 *
 * <p>Thematically the closest neighbour to the asteroid feed: both answer "what is
 * happening out there that could affect Earth". Operationally it is the opposite of
 * the other NASA APIs - a query over a month of events routinely takes 60 to 90
 * seconds, which is why it has its own RestClient, its own timeout budget, only one
 * retry, and a bulkhead.
 */
public interface NasaDonkiClient {

    /** Coronal mass ejections that began within the window. */
    List<CoronalMassEjection> coronalMassEjections(LocalDate from, LocalDate to);

    /**
     * Geomagnetic storms that began within the window.
     *
     * <p>Measured by the Kp index; see {@link GeomagneticStorm}.
     */
    List<GeomagneticStorm> geomagneticStorms(LocalDate from, LocalDate to);

    /**
     * Solar flares that began within the window.
     *
     * <p>Classified A/B/C/M/X by X-ray intensity; see {@link SolarFlare}.
     */
    List<SolarFlare> solarFlares(LocalDate from, LocalDate to);
}
