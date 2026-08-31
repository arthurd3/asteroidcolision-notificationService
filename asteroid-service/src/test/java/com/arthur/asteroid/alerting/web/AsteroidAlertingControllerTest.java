package com.arthur.asteroid.alerting.web;

import com.arthur.asteroid.alerting.config.NasaProperties;
import com.arthur.asteroid.alerting.config.NasaPropertiesFixture;
import com.arthur.asteroid.alerting.domain.AlertSummary;
import com.arthur.asteroid.alerting.domain.AsteroidAlertingService;
import com.arthur.asteroid.alerting.nasa.NasaUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AsteroidAlertingController.class)
@Import({ApiExceptionHandler.class, AsteroidAlertingControllerTest.Props.class})
class AsteroidAlertingControllerTest {

    private static final String URL = "/api/v1/asteroid-alerting/alert";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AsteroidAlertingService alertingService;

    @TestConfiguration
    static class Props {
        @Bean
        NasaProperties nasaProperties() {
            return NasaPropertiesFixture.pointingAt("http://nasa.test");
        }
    }

    @Test
    @DisplayName("returns a summary rather than an empty body")
    void returnsSummary() throws Exception {
        given(alertingService.alert())
                .willReturn(new AlertSummary(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 8), 46, 4, 4));

        mockMvc.perform(post(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scanned").value(46))
                .andExpect(jsonPath("$.hazardous").value(4))
                .andExpect(jsonPath("$.published").value(4));
    }

    @Test
    @DisplayName("rejects a reversed window with an RFC 9457 problem document")
    void rejectsReversedWindow() throws Exception {
        mockMvc.perform(post(URL).param("from", "2026-03-08").param("to", "2026-03-01"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Invalid scan window"))
                .andExpect(jsonPath("$.type").value("https://asteroid.arthur.com/problems/invalid-scan-window"));
    }

    @Test
    @DisplayName("rejects a window longer than the feed's 7-day limit")
    void rejectsOversizedWindow() throws Exception {
        mockMvc.perform(post(URL).param("from", "2026-03-01").param("to", "2026-03-20"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("at most 7 days")));
    }

    @Test
    @DisplayName("rejects only one half of the window being supplied")
    void rejectsHalfWindow() throws Exception {
        mockMvc.perform(post(URL).param("from", "2026-03-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("reports an unreachable NASA feed as 503, not a bare 500")
    void mapsUpstreamFailureTo503() throws Exception {
        given(alertingService.alert()).willThrow(new NasaUnavailableException("feed said 429"));

        mockMvc.perform(post(URL))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Upstream unavailable"));
    }

    @Test
    @DisplayName("accepts a valid explicit window")
    void acceptsValidWindow() throws Exception {
        given(alertingService.alert(any(), any()))
                .willReturn(new AlertSummary(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 5), 1, 0, 0));

        mockMvc.perform(post(URL).param("from", "2026-03-01").param("to", "2026-03-05"))
                .andExpect(status().isOk());
    }
}
