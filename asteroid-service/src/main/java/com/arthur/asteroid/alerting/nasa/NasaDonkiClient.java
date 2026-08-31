package com.arthur.asteroid.alerting.nasa;

import com.arthur.asteroid.alerting.nasa.dto.donki.CoronalMassEjection;

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
}
