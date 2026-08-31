package com.arthur.asteroid.alerting.nasa;

import com.arthur.asteroid.alerting.nasa.dto.apod.ApodEntry;

import java.time.LocalDate;

/** Reads NASA's Astronomy Picture of the Day. */
public interface NasaApodClient {

    /**
     * @param date the day to fetch, or {@code null} for today. NASA publishes on its
     *             own schedule in US Eastern time, so "today" can legitimately 404
     *             for a few hours after UTC midnight
     */
    ApodEntry pictureOfTheDay(LocalDate date);
}
