package com.arthur.asteroid.alerting.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Caches NASA responses, because the rate limit is the binding constraint.
 *
 * <p>DEMO_KEY allows 30 requests an hour across every api.nasa.gov endpoint - not 30
 * per endpoint. One load of the front page asks for the picture of the day, a week of
 * the NEO feed and the latest EPIC frames, so a handful of refreshes exhausts the
 * hour and everything afterwards is a 429. Without this the front end spends most of
 * its life rendering the error page, which looks like a bug in this project rather
 * than a quota.
 *
 * <p>The TTL is short on purpose. This is not a durable cache and does not try to be:
 * ten minutes is long enough that clicking around a site costs one upstream call per
 * resource, and short enough that nobody has to reason about staleness. NASA's data
 * changes on the order of days.
 *
 * <p>Only the read-only endpoints are cached. {@code findAsteroids} is deliberately
 * NOT, because it is what the alerting scan calls: a cached scan would silently
 * re-publish a stale window, and the whole point of the scan is that it looked just
 * now.
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
public class NasaCacheConfig {

    /**
     * Cache names, referenced by {@code @Cacheable} on the clients.
     *
     * <p>One name per method, never one per API, and that is load bearing rather than
     * tidy. Spring's default {@code SimpleKeyGenerator} builds a key from the method
     * ARGUMENTS ONLY - the method itself is not part of it. DONKI's three endpoints
     * all take {@code (LocalDate from, LocalDate to)}, so sharing a cache between
     * them would mean a request for solar flares over a window returned whatever
     * coronal mass ejections had been fetched for the same window. Silently, and
     * looking entirely plausible.
     */
    public static final String APOD = "nasaApod";
    public static final String NEO_LOOKUP = "nasaNeoLookup";
    public static final String NEO_BROWSE = "nasaNeoBrowse";
    public static final String DONKI_CME = "nasaDonkiCme";
    public static final String DONKI_GST = "nasaDonkiGst";
    public static final String DONKI_FLR = "nasaDonkiFlr";
    public static final String EPIC_FRAMES = "nasaEpicFrames";
    public static final String EPIC_DATES = "nasaEpicDates";
    public static final String EPIC_IMAGE = "nasaEpicImage";

    @Bean
    CaffeineCacheManager cacheManager(NasaCacheProperties properties) {
        final CaffeineCacheManager manager = new CaffeineCacheManager(
                APOD, NEO_LOOKUP, NEO_BROWSE,
                DONKI_CME, DONKI_GST, DONKI_FLR,
                EPIC_FRAMES, EPIC_DATES);
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(properties.ttl())
                .maximumSize(properties.maximumSize())
                .recordStats());

        // Images get their own settings: a couple of megabytes each, so a few hundred
        // of them would be a heap problem rather than a cache. They also never change
        // once published, so they can be held far longer than metadata.
        manager.registerCustomCache(EPIC_IMAGE, Caffeine.newBuilder()
                .expireAfterWrite(properties.imageTtl())
                .maximumSize(properties.imageMaximumSize())
                .recordStats()
                .build());
        return manager;
    }

    /**
     * @param ttl              how long a NASA response stays fresh
     * @param maximumSize      entries per metadata cache
     * @param imageTtl         EPIC archive frames are immutable, so this can be long
     * @param imageMaximumSize deliberately small: each entry is roughly 2 MB
     */
    @Validated
    @ConfigurationProperties(prefix = "asteroid.nasa.cache")
    public record NasaCacheProperties(
            Duration ttl,
            long maximumSize,
            Duration imageTtl,
            long imageMaximumSize
    ) {
    }
}
