package com.arthur.asteroid.alerting.web;

import com.arthur.asteroid.alerting.config.NasaProperties;
import com.arthur.asteroid.alerting.config.NasaPropertiesFixture;
import com.arthur.asteroid.alerting.nasa.NasaDonkiClient;
import com.arthur.asteroid.alerting.nasa.NasaUnavailableException;
import com.arthur.asteroid.alerting.nasa.dto.donki.CoronalMassEjection;
import com.arthur.asteroid.alerting.nasa.dto.donki.DonkiInstrument;
import com.arthur.asteroid.alerting.nasa.dto.donki.GeomagneticStorm;
import com.arthur.asteroid.alerting.nasa.dto.donki.KpIndexReading;
import com.arthur.asteroid.alerting.nasa.dto.donki.LinkedEvent;
import com.arthur.asteroid.alerting.nasa.dto.donki.SolarFlare;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DonkiController.class)
@Import({ApiExceptionHandler.class, DonkiControllerTest.Fixtures.class})
class DonkiControllerTest {

    private static final String CME = "/api/v1/nasa/donki/cme";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NasaDonkiClient donkiClient;

    @TestConfiguration
    static class Fixtures {
        @Bean
        NasaProperties nasaProperties() {
            return NasaPropertiesFixture.pointingAt("http://nasa.test");
        }

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-08-31T09:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Test
    @DisplayName("defaults to the configured window, counted back from today")
    void defaultsWindow() throws Exception {
        given(donkiClient.coronalMassEjections(any(), any())).willReturn(List.of(cme()));

        mockMvc.perform(get(CME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].activityID").value("2026-08-02T10:45:00-CME-001"))
                .andExpect(jsonPath("$[0].instruments[0].displayName").value("GOES: CCOR-1"));

        // default-window-days is 30 in the fixture
        verify(donkiClient).coronalMassEjections(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
    }

    @Test
    @DisplayName("accepts an explicit window")
    void acceptsExplicitWindow() throws Exception {
        given(donkiClient.coronalMassEjections(any(), any())).willReturn(List.of());

        mockMvc.perform(get(CME).param("from", "2026-07-01").param("to", "2026-07-15"))
                .andExpect(status().isOk());

        verify(donkiClient).coronalMassEjections(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 15));
    }

    @Test
    @DisplayName("refuses a window wider than this service allows, without calling DONKI")
    void refusesOversizedWindow() throws Exception {
        // the cap is ours, not NASA's: DONKI would accept a year and spend minutes on it
        mockMvc.perform(get(CME).param("from", "2025-01-01").param("to", "2026-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail")
                        .value(org.hamcrest.Matchers.containsString("at most 90 days")));

        verify(donkiClient, never()).coronalMassEjections(any(), any());
    }

    @Test
    @DisplayName("a quiet window is an empty array, not a 404")
    void returnsEmptyArray() throws Exception {
        given(donkiClient.coronalMassEjections(any(), any())).willReturn(List.of());

        mockMvc.perform(get(CME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("a full bulkhead is 429, not 503: nothing is broken, there are just too many callers")
    void translatesBulkheadFull() throws Exception {
        final Bulkhead full = Bulkhead.of("nasaDonki", BulkheadConfig.custom().maxConcurrentCalls(1).build());
        given(donkiClient.coronalMassEjections(any(), any()))
                .willThrow(BulkheadFullException.createBulkheadFullException(full));

        mockMvc.perform(get(CME))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Too many concurrent requests"))
                .andExpect(jsonPath("$.type")
                        .value("https://asteroid.arthur.com/problems/too-many-concurrent-requests"));
    }

    @Test
    @DisplayName("an unreachable DONKI becomes 503 problem+json")
    void translatesUpstreamFailure() throws Exception {
        given(donkiClient.coronalMassEjections(any(), any()))
                .willThrow(new NasaUnavailableException("Could not reach NASA DONKI"));

        mockMvc.perform(get(CME))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Upstream unavailable"));
    }

    @Test
    @DisplayName("serves geomagnetic storms on their own path")
    void servesGeomagneticStorms() throws Exception {
        given(donkiClient.geomagneticStorms(any(), any())).willReturn(List.of(
                new GeomagneticStorm("2026-08-12T18:00:00-GST-001",
                        OffsetDateTime.parse("2026-08-12T18:00Z"),
                        List.of(new KpIndexReading(OffsetDateTime.parse("2026-08-13T00:00Z"), 7.33, "NOAA")),
                        List.of(new LinkedEvent("2026-08-11T03:36:00-CME-001")),
                        "https://webtools.ccmc.gsfc.nasa.gov/DONKI/view/GST/1/-1")));

        mockMvc.perform(get("/api/v1/nasa/donki/gst"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].gstID").value("2026-08-12T18:00:00-GST-001"))
                .andExpect(jsonPath("$[0].allKpIndex[0].kpIndex").value(7.33))
                .andExpect(jsonPath("$[0].linkedEvents[0].activityID")
                        .value("2026-08-11T03:36:00-CME-001"));

        verify(donkiClient).geomagneticStorms(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
    }

    @Test
    @DisplayName("serves solar flares on their own path")
    void servesSolarFlares() throws Exception {
        given(donkiClient.solarFlares(any(), any())).willReturn(List.of(
                new SolarFlare("2026-08-11T02:58:00-FLR-001",
                        List.of(new DonkiInstrument("GOES-P: EXIS 1.0-8.0")),
                        OffsetDateTime.parse("2026-08-11T02:58Z"),
                        OffsetDateTime.parse("2026-08-11T03:14Z"),
                        OffsetDateTime.parse("2026-08-11T03:31Z"),
                        "M1.4", "N12E45", 14208, "", List.of(),
                        "https://webtools.ccmc.gsfc.nasa.gov/DONKI/view/FLR/1/-1")));

        mockMvc.perform(get("/api/v1/nasa/donki/flr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].flrID").value("2026-08-11T02:58:00-FLR-001"))
                .andExpect(jsonPath("$[0].classType").value("M1.4"))
                .andExpect(jsonPath("$[0].activeRegionNum").value(14208));

        verify(donkiClient).solarFlares(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
    }

    @Test
    @DisplayName("all three DONKI paths share the same window validation")
    void allPathsShareWindowValidation() throws Exception {
        for (final String path : List.of("cme", "gst", "flr")) {
            mockMvc.perform(get("/api/v1/nasa/donki/" + path)
                            .param("from", "2025-01-01").param("to", "2026-01-01"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail")
                            .value(org.hamcrest.Matchers.containsString("at most 90 days")));
        }
    }

    private static CoronalMassEjection cme() {
        return new CoronalMassEjection(
                "2026-08-02T10:45:00-CME-001", "M2M_CATALOG",
                OffsetDateTime.parse("2026-08-02T10:45Z"),
                List.of(new DonkiInstrument("GOES: CCOR-1")),
                "", null, "A faint CME.",
                OffsetDateTime.parse("2026-08-02T14:02Z"),
                "https://webtools.ccmc.gsfc.nasa.gov/DONKI/view/CME/1/-1");
    }
}
