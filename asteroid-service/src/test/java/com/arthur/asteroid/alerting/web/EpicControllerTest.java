package com.arthur.asteroid.alerting.web;

import com.arthur.asteroid.alerting.config.NasaProperties;
import com.arthur.asteroid.alerting.config.NasaPropertiesFixture;
import com.arthur.asteroid.alerting.nasa.NasaEpicClient;
import com.arthur.asteroid.alerting.nasa.NasaUnavailableException;
import com.arthur.asteroid.alerting.nasa.dto.epic.EpicCoordinates;
import com.arthur.asteroid.alerting.nasa.dto.epic.EpicImage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EpicController.class)
@Import({ApiExceptionHandler.class, EpicImageAssembler.class, EpicControllerTest.Fixtures.class})
class EpicControllerTest {

    private static final String BASE = "/api/v1/nasa/epic";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NasaEpicClient epicClient;

    @TestConfiguration
    static class Fixtures {
        @Bean
        NasaProperties nasaProperties() {
            return NasaPropertiesFixture.pointingAt("http://nasa.test");
        }
    }

    @Test
    @DisplayName("returns views whose image path points back at this service")
    void returnsProxiedViews() throws Exception {
        given(epicClient.naturalImages(null)).willReturn(List.of(frame()));

        mockMvc.perform(get(BASE + "/natural"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].identifier").value("20260829004554"))
                .andExpect(jsonPath("$[0].imagePath")
                        .value("/api/v1/nasa/epic/image/natural/2026/08/29/epic_1b_20260829004554"));
    }

    @Test
    @DisplayName("no response anywhere contains the API key or a NASA archive URL")
    void neverLeaksTheApiKey() throws Exception {
        // The single most important assertion in this class. EpicImageView has no
        // field capable of holding a NASA URL, and this is what proves the controller
        // did not reintroduce one.
        given(epicClient.naturalImages(null)).willReturn(List.of(frame()));

        final MvcResult result = mockMvc.perform(get(BASE + "/natural")).andReturn();
        final String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body)
                .doesNotContain("api_key")
                .doesNotContain(NasaPropertiesFixture.API_KEY)
                .doesNotContain("api.nasa.gov");
    }

    @Test
    @DisplayName("serves the PNG with a long, immutable cache lifetime")
    void servesPngWithCacheHeaders() throws Exception {
        final byte[] png = {(byte) 0x89, 'P', 'N', 'G'};
        given(epicClient.naturalImagePng(
                "/EPIC/archive/natural/2026/08/29/png/epic_1b_20260829004554.png")).willReturn(png);

        final MvcResult result = mockMvc.perform(
                        get(BASE + "/image/natural/2026/08/29/epic_1b_20260829004554"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                // the archive is immutable once published, so this can be aggressive
                .andExpect(header().string("Cache-Control",
                        org.hamcrest.Matchers.containsString("max-age=604800")))
                .andExpect(header().string("Cache-Control",
                        org.hamcrest.Matchers.containsString("immutable")))
                .andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(png);
    }

    @ParameterizedTest(name = "a crafted image segment ''{0}'' is refused")
    @ValueSource(strings = {"arbitrary", "epic_1b_2026", "epic_1b_20260829004554.png", "not_epic_20260829004554"})
    @DisplayName("a crafted image segment never reaches the upstream client")
    void refusesCraftedImageSegment(final String image) throws Exception {
        mockMvc.perform(get(BASE + "/image/natural/2026/08/29/" + image))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type")
                        .value("https://asteroid.arthur.com/problems/unknown-epic-image"));

        verify(epicClient, never()).naturalImagePng(any());
    }

    @Test
    @DisplayName("an unknown collection is refused")
    void refusesUnknownCollection() throws Exception {
        mockMvc.perform(get(BASE + "/image/planetary/2026/08/29/epic_1b_20260829004554"))
                .andExpect(status().isBadRequest());

        verify(epicClient, never()).naturalImagePng(any());
    }

    @Test
    @DisplayName("an impossible date is refused before it becomes a URL")
    void refusesImpossibleDate() throws Exception {
        mockMvc.perform(get(BASE + "/image/natural/2026/13/40/epic_1b_20260829004554"))
                .andExpect(status().isBadRequest());

        verify(epicClient, never()).naturalImagePng(any());
    }

    @Test
    @DisplayName("returns the available dates for a date picker")
    void returnsAvailableDates() throws Exception {
        given(epicClient.availableNaturalDates())
                .willReturn(List.of(LocalDate.of(2026, 8, 29), LocalDate.of(2026, 8, 28)));

        mockMvc.perform(get(BASE + "/natural/dates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("2026-08-29"))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("an unreachable EPIC becomes 503 problem+json")
    void translatesUpstreamFailure() throws Exception {
        given(epicClient.naturalImages(any()))
                .willThrow(new NasaUnavailableException("NASA EPIC returned 503"));

        mockMvc.perform(get(BASE + "/natural"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Upstream unavailable"));
    }

    private static EpicImage frame() {
        return new EpicImage("20260829004554", "Taken by EPIC aboard DSCOVR.",
                "epic_1b_20260829004554", "04",
                new EpicCoordinates(8.049316, 175.246582),
                LocalDateTime.of(2026, 8, 29, 0, 41, 6));
    }
}
