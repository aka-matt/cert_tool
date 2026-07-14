package io.github.certtool.domain.certificate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ValidityWindow")
class ValidityWindowTest {

    private static final Instant NOW = Instant.parse("2026-07-13T12:00:00Z");

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("rejects null notBefore")
        void rejectsNullNotBefore() {
            assertThatThrownBy(() -> new ValidityWindow(null, NOW.plus(Duration.ofDays(30))))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("notBefore");
        }

        @Test
        @DisplayName("rejects null notAfter")
        void rejectsNullNotAfter() {
            assertThatThrownBy(() -> new ValidityWindow(NOW, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("notAfter");
        }

        @Test
        @DisplayName("rejects notAfter before notBefore")
        void rejectsInvertedWindow() {
            assertThatThrownBy(() -> new ValidityWindow(NOW, NOW.minus(Duration.ofDays(1))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("notAfter");
        }
    }

    @Nested
    @DisplayName("validAt")
    class ValidAt {

        @Test
        @DisplayName("returns VALID when instant is within window")
        void validInsideWindow() {
            ValidityWindow w = new ValidityWindow(NOW, NOW.plus(Duration.ofDays(30)));
            assertThat(w.validAt(NOW.plus(Duration.ofDays(10)))).isEqualTo(ValidityState.VALID);
            assertThat(w.validAt(NOW)).isEqualTo(ValidityState.VALID);
            assertThat(w.validAt(NOW.plus(Duration.ofDays(30)))).isEqualTo(ValidityState.VALID);
        }

        @Test
        @DisplayName("returns NOT_YET_VALID before notBefore")
        void notYetValid() {
            ValidityWindow w = new ValidityWindow(NOW, NOW.plus(Duration.ofDays(30)));
            assertThat(w.validAt(NOW.minus(Duration.ofSeconds(1)))).isEqualTo(ValidityState.NOT_YET_VALID);
        }

        @Test
        @DisplayName("returns EXPIRED after notAfter")
        void expired() {
            ValidityWindow w = new ValidityWindow(NOW, NOW.plus(Duration.ofDays(30)));
            assertThat(w.validAt(NOW.plus(Duration.ofDays(30)).plus(Duration.ofSeconds(1))))
                    .isEqualTo(ValidityState.EXPIRED);
        }
    }
}