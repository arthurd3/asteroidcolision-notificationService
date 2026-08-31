package com.arthur.asteroid.alerting.nasa.dto.neo;

import java.time.LocalDate;
import java.util.List;

/**
 * Builders for the NEO records, so tests do not construct them positionally.
 *
 * <p>{@link Asteroid} has eleven components and {@link CloseApproachData} six, and
 * both grow whenever another NeoWs field turns out to be worth showing. Every test
 * that called {@code new Asteroid(...)} directly broke the moment {@code orbital_data}
 * was added - which is exactly the churn this file exists to absorb. Adding a twelfth
 * component should mean editing one default here, not every test that ever needed an
 * asteroid.
 *
 * <p>The defaults describe the shape the <em>feed</em> returns: no orbital data, no
 * JPL url, no sentry flag. Tests that care about lookup or browse opt into those.
 */
public final class NeoFixtures {

    public static final LocalDate APPROACH_DATE = LocalDate.of(2026, 3, 4);

    private NeoFixtures() {
    }

    /** A feed-shaped asteroid with one usable close approach. */
    public static Asteroid asteroid(final String id, final String name, final boolean hazardous) {
        return asteroid(id, name, hazardous, List.of(approach("54321.5")));
    }

    /** A feed-shaped asteroid with the approach list spelled out. */
    public static Asteroid asteroid(final String id,
                                    final String name,
                                    final boolean hazardous,
                                    final List<CloseApproachData> approaches) {
        return new Asteroid(id, id, name, null, null, null,
                diameter(100, 300), hazardous, null, approaches, null);
    }

    /** The same object as lookup and browse return it: with an orbit attached. */
    public static Asteroid withOrbit(final Asteroid base, final OrbitalData orbitalData) {
        return new Asteroid(base.id(), base.neoReferenceId(), base.name(), base.designation(),
                base.nasaJplUrl(), base.absoluteMagnitudeH(), base.estimatedDiameter(),
                base.potentiallyHazardous(), base.sentryObject(), base.closeApproachData(),
                orbitalData);
    }

    public static CloseApproachData approach(final String kilometers) {
        return approach(APPROACH_DATE, kilometers);
    }

    public static CloseApproachData approach(final LocalDate date, final String kilometers) {
        return new CloseApproachData(date, null, null, null, missDistance(kilometers), "Earth");
    }

    public static MissDistance missDistance(final String kilometers) {
        return new MissDistance(null, null, kilometers, null);
    }

    public static EstimatedDiameter diameter(final double min, final double max) {
        return new EstimatedDiameter(new DiameterRange(min, max));
    }
}
