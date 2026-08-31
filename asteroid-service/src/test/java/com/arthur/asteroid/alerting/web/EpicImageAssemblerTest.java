package com.arthur.asteroid.alerting.web;

import com.arthur.asteroid.alerting.config.NasaPropertiesFixture;
import com.arthur.asteroid.alerting.nasa.dto.epic.EpicCoordinates;
import com.arthur.asteroid.alerting.nasa.dto.epic.EpicImage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The two halves of EPIC's key-safety rule.
 *
 * <p>Going out: nothing rendered into a page may carry the API key. Coming back in:
 * nothing a caller sends may reshape the upstream URL, because that URL is signed
 * with the key - an open proxy for arbitrary api.nasa.gov endpoints would be worse
 * than the leak the proxy exists to prevent.
 */
class EpicImageAssemblerTest {

    private final EpicImageAssembler assembler = new EpicImageAssembler();

    private static final EpicImage FRAME = new EpicImage(
            "20260829004554",
            "This image was taken by NASA's EPIC camera onboard the NOAA DSCOVR spacecraft",
            "epic_1b_20260829004554",
            "04",
            new EpicCoordinates(8.049316, 175.246582),
            LocalDateTime.of(2026, 8, 29, 0, 41, 6));

    @Test
    @DisplayName("the view's image path is on this service, and carries no API key")
    void viewPathCarriesNoKey() {
        final EpicImageView view = assembler.toView(FRAME, "natural");

        assertThat(view.imagePath())
                .isEqualTo("/api/v1/nasa/epic/image/natural/2026/08/29/epic_1b_20260829004554")
                .doesNotContain("api_key")
                .doesNotContain(NasaPropertiesFixture.API_KEY)
                .doesNotContain("api.nasa.gov")
                .doesNotContain("?");
    }

    @Test
    @DisplayName("the view keeps the metadata a page needs to caption the frame")
    void viewKeepsMetadata() {
        final EpicImageView view = assembler.toView(FRAME, "natural");

        assertThat(view.identifier()).isEqualTo("20260829004554");
        assertThat(view.caption()).contains("DSCOVR");
        assertThat(view.centroidCoordinates().lat()).isEqualTo(8.049316);
        assertThat(view.date()).isEqualTo(LocalDateTime.of(2026, 8, 29, 0, 41, 6));
    }

    @Test
    @DisplayName("rebuilds the upstream archive path, appending .png itself")
    void rebuildsArchivePath() {
        assertThat(assembler.archivePath("natural", 2026, 8, 29, "epic_1b_20260829004554"))
                .isEqualTo("/EPIC/archive/natural/2026/08/29/png/epic_1b_20260829004554.png");
    }

    @Test
    @DisplayName("the round trip is consistent: what toView emits, archivePath accepts")
    void roundTripsWithTheView() {
        final String path = assembler.toView(FRAME, "natural").imagePath();
        final String[] parts = path.substring(EpicImageAssembler.PROXY_PREFIX.length()).split("/");

        assertThatCode(() -> assembler.archivePath(parts[0],
                Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]),
                parts[4])).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "refuses image segment: {0}")
    @ValueSource(strings = {
            "../../../../planetary/apod",
            "epic_1b_20260829004554.png",
            "epic_1b_20260829004554/../../etc",
            "../DONKI/CME",
            "epic_1b_2026",
            "arbitrary",
            "epic_1b_20260829004554?api_key=stolen",
            ""
    })
    @DisplayName("refuses anything that is not exactly an EPIC frame name")
    void refusesCraftedImageNames(final String image) {
        // Each of these, concatenated onto api.nasa.gov with our key attached, would
        // fetch something other than the intended photograph.
        assertThatThrownBy(() -> assembler.archivePath("natural", 2026, 8, 29, image))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    @DisplayName("refuses a null image name")
    void refusesNullImageName() {
        assertThatThrownBy(() -> assembler.archivePath("natural", 2026, 8, 29, null))
                .isInstanceOf(InvalidRequestException.class);
    }

    @ParameterizedTest(name = "refuses collection: {0}")
    @ValueSource(strings = {"../planetary", "Natural", "png", "", "natural/../.."})
    @DisplayName("only the two real collections are accepted")
    void refusesUnknownCollections(final String collection) {
        assertThatThrownBy(() ->
                assembler.archivePath(collection, 2026, 8, 29, "epic_1b_20260829004554"))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    @DisplayName("accepts the enhanced collection as well as natural")
    void acceptsEnhancedCollection() {
        assertThat(assembler.archivePath("enhanced", 2026, 8, 29, "epic_1b_20260829004554"))
                .startsWith("/EPIC/archive/enhanced/");
    }

    @Test
    @DisplayName("refuses a date that does not exist")
    void refusesImpossibleDate() {
        assertThatThrownBy(() -> assembler.archivePath("natural", 2026, 13, 40, "epic_1b_20260829004554"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("not a real date");
    }

    @Test
    @DisplayName("zero-pads month and day, because the archive is laid out that way")
    void zeroPadsDateParts() {
        assertThat(assembler.archivePath("natural", 2026, 1, 5, "epic_1b_20260105004554"))
                .contains("/2026/01/05/");
    }
}
