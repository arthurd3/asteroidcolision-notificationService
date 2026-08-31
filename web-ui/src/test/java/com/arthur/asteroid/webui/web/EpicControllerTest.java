package com.arthur.asteroid.webui.web;

import com.arthur.asteroid.webui.backend.AsteroidServiceClient;
import com.arthur.asteroid.webui.backend.BackendUnavailableException;
import com.arthur.asteroid.webui.backend.dto.EpicImageView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * A slice test, so it asserts the view name and the model and never the HTML - there
 * is no servlet container here, so MockMvc only reports the forward. Rendering is
 * covered by {@code WebUiPagesIT}.
 */
@WebMvcTest(EpicController.class)
@Import(Formats.class)
class EpicControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AsteroidServiceClient asteroidService;

    @Test
    @DisplayName("the date picker is ordered newest first, whatever order NASA sends")
    void availableDatesAreNewestFirst() throws Exception {
        // NASA returns over three thousand dates, oldest first. Rendered unsorted, the
        // picker showed 2015 at the top while 2026 imagery was on screen - the dropdown
        // did not even contain the date being displayed.
        given(asteroidService.epicNatural(any())).willReturn(List.of(frame()));
        given(asteroidService.epicAvailableDates()).willReturn(List.of(
                LocalDate.of(2015, 6, 13),
                LocalDate.of(2020, 1, 1),
                LocalDate.of(2026, 8, 29)));

        mockMvc.perform(get("/epic"))
                .andExpect(status().isOk())
                .andExpect(view().name("epic"))
                .andExpect(model().attribute("availableDates", List.of(
                        LocalDate.of(2026, 8, 29),
                        LocalDate.of(2020, 1, 1),
                        LocalDate.of(2015, 6, 13))));
    }

    @Test
    @DisplayName("losing the date index does not lose the photographs")
    void missingDateIndexStillRendersFrames() throws Exception {
        // the picker is a convenience; the frames are the page
        given(asteroidService.epicNatural(any())).willReturn(List.of(frame()));
        given(asteroidService.epicAvailableDates())
                .willThrow(new BackendUnavailableException("asteroid-service", null, null, "down", null));

        mockMvc.perform(get("/epic"))
                .andExpect(status().isOk())
                .andExpect(view().name("epic"))
                .andExpect(model().attribute("availableDates", List.of()))
                .andExpect(model().attributeExists("frames"));
    }

    @Test
    @DisplayName("an explicit date is passed through to the backend")
    void passesRequestedDateThrough() throws Exception {
        given(asteroidService.epicNatural(LocalDate.of(2026, 8, 29))).willReturn(List.of(frame()));
        given(asteroidService.epicAvailableDates()).willReturn(List.of());

        mockMvc.perform(get("/epic").param("date", "2026-08-29"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedDate", LocalDate.of(2026, 8, 29)));
    }

    private static EpicImageView frame() {
        return new EpicImageView("20260829004554", "Taken by EPIC aboard DSCOVR.",
                "epic_1b_20260829004554", LocalDateTime.of(2026, 8, 29, 0, 41, 6),
                new EpicImageView.CoordinatesView(8.049316, 175.246582),
                "/api/v1/nasa/epic/image/natural/2026/08/29/epic_1b_20260829004554");
    }
}
