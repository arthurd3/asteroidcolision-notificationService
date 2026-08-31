package com.arthur.asteroid.alerting.web;

import com.arthur.asteroid.alerting.config.NasaProperties;
import com.arthur.asteroid.alerting.config.NasaPropertiesFixture;
import com.arthur.asteroid.alerting.nasa.NasaNeoClient;
import com.arthur.asteroid.alerting.nasa.NasaUnavailableException;
import com.arthur.asteroid.alerting.nasa.dto.neo.NeoBrowsePage;
import com.arthur.asteroid.alerting.nasa.dto.neo.NeoFixtures;
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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NeoCatalogController.class)
@Import({ApiExceptionHandler.class, NeoCatalogControllerTest.Fixtures.class})
class NeoCatalogControllerTest {

    private static final String BASE = "/api/v1/nasa/neo";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NasaNeoClient neoClient;

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

    // ---------------------------------------------------------------- feed

    @Test
    @DisplayName("defaults the feed window to today through the configured lookahead")
    void feedDefaultsWindow() throws Exception {
        given(neoClient.findAsteroids(any(), any()))
                .willReturn(List.of(NeoFixtures.asteroid("1", "Rock", true)));

        mockMvc.perform(get(BASE + "/feed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-08-31"))
                .andExpect(jsonPath("$.to").value("2026-09-07"))
                .andExpect(jsonPath("$.elementCount").value(1))
                .andExpect(jsonPath("$.objects[0].name").value("Rock"));
    }

    @Test
    @DisplayName("accepts an explicit window")
    void feedAcceptsExplicitWindow() throws Exception {
        given(neoClient.findAsteroids(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5)))
                .willReturn(List.of());

        mockMvc.perform(get(BASE + "/feed").param("from", "2026-09-01").param("to", "2026-09-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elementCount").value(0));
    }

    @Test
    @DisplayName("rejects a window wider than the feed's own limit, without calling NASA")
    void feedRejectsOversizedWindow() throws Exception {
        mockMvc.perform(get(BASE + "/feed").param("from", "2026-09-01").param("to", "2026-09-30"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Invalid scan window"))
                .andExpect(jsonPath("$.type")
                        .value("https://asteroid.arthur.com/problems/invalid-scan-window"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("at most 7 days")));

        verify(neoClient, never()).findAsteroids(any(), any());
    }

    @Test
    @DisplayName("rejects a reversed window")
    void feedRejectsReversedWindow() throws Exception {
        mockMvc.perform(get(BASE + "/feed").param("from", "2026-09-05").param("to", "2026-09-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value(org.hamcrest.Matchers.containsString("must not be before")));
    }

    @Test
    @DisplayName("rejects half a window")
    void feedRejectsHalfWindow() throws Exception {
        mockMvc.perform(get(BASE + "/feed").param("from", "2026-09-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value(org.hamcrest.Matchers.containsString("supplied together")));
    }

    // -------------------------------------------------------------- browse

    @Test
    @DisplayName("browse defaults the page size to the configured value")
    void browseDefaultsPageSize() throws Exception {
        given(neoClient.browse(anyInt(), anyInt())).willReturn(page());

        mockMvc.perform(get(BASE + "/browse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.total_elements").value(62193))
                .andExpect(jsonPath("$.page.total_pages").value(3110));

        verify(neoClient).browse(0, 20);
    }

    @Test
    @DisplayName("browse passes through an explicit page and size")
    void browseAcceptsExplicitPaging() throws Exception {
        given(neoClient.browse(anyInt(), anyInt())).willReturn(page());

        mockMvc.perform(get(BASE + "/browse").param("page", "7").param("size", "5"))
                .andExpect(status().isOk());

        verify(neoClient).browse(7, 5);
    }

    @Test
    @DisplayName("browse rejects a negative page")
    void browseRejectsNegativePage() throws Exception {
        mockMvc.perform(get(BASE + "/browse").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid page"));

        verify(neoClient, never()).browse(anyInt(), anyInt());
    }

    @ParameterizedTest(name = "browse rejects size={0}")
    @ValueSource(strings = {"0", "21", "-3"})
    @DisplayName("browse rejects a page size NASA would refuse")
    void browseRejectsBadPageSize(final String size) throws Exception {
        mockMvc.perform(get(BASE + "/browse").param("size", size))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid page size"));

        verify(neoClient, never()).browse(anyInt(), anyInt());
    }

    // -------------------------------------------------------------- lookup

    @Test
    @DisplayName("looks one object up by id")
    void looksUpById() throws Exception {
        given(neoClient.lookup("2000433"))
                .willReturn(NeoFixtures.asteroid("2000433", "433 Eros", false));

        mockMvc.perform(get(BASE + "/2000433"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("433 Eros"));
    }

    @ParameterizedTest(name = "a non-numeric id like ''{0}'' does not reach the client")
    @ValueSource(strings = {"abc", "12", "../planetary/apod", "2000433x"})
    @DisplayName("the id mapping only matches plain numeric ids")
    void refusesNonNumericIds(final String id) throws Exception {
        // The id is concatenated onto the upstream request path, so anything that is
        // not a plain id could reshape the URL sent to api.nasa.gov - with the API key
        // attached. The mapping constraint is the guard, so no route matches at all.
        mockMvc.perform(get(BASE + "/" + id)).andExpect(status().isNotFound());

        verify(neoClient, never()).lookup(any());
    }

    @Test
    @DisplayName("an unreachable NASA becomes 503 problem+json")
    void translatesUpstreamFailure() throws Exception {
        given(neoClient.lookup(eq("2000433")))
                .willThrow(new NasaUnavailableException("NASA NEO feed returned 503"));

        mockMvc.perform(get(BASE + "/2000433"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Upstream unavailable"));
    }

    private static NeoBrowsePage page() {
        return new NeoBrowsePage(
                List.of(NeoFixtures.asteroid("2000433", "433 Eros", false)),
                new NeoBrowsePage.PageInfo(20, 62193L, 3110, 0));
    }
}
