package io.github.certtool.domain.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LoadFailure")
class LoadFailureTest {

    @Test
    @DisplayName("factory produces a failure with no cause")
    void factoryNoCause() {
        LoadFailure f = LoadFailure.of(LoadFailureReason.WRONG_STORE_PASSWORD, "Wrong password");

        assertThat(f.reason()).isEqualTo(LoadFailureReason.WRONG_STORE_PASSWORD);
        assertThat(f.userMessage()).isEqualTo("Wrong password");
        assertThat(f.cause()).isEqualTo(Optional.empty());
        assertThat(f.retryable()).isFalse();
        assertThat(f.needsPassword()).isFalse();
        assertThat(f.technicalReason()).isEqualTo("WRONG_STORE_PASSWORD");
    }

    @Test
    @DisplayName("factory with cause wraps the throwable")
    void factoryWithCause() {
        IllegalStateException ex = new IllegalStateException("boom");
        LoadFailure f = LoadFailure.of(LoadFailureReason.CORRUPTED_KEYSTORE, "Cannot parse", ex);

        assertThat(f.cause()).contains(ex);
        assertThat(f.technicalReason()).contains("CORRUPTED_KEYSTORE").contains("IllegalStateException");
    }

    @Test
    @DisplayName("empty technical reason falls back to the reason name")
    void blankTechnicalReasonFallsBack() {
        LoadFailure f = new LoadFailure(
                LoadFailureReason.EMPTY_INPUT,
                "empty",
                "  ",
                true,
                false,
                Optional.empty());

        assertThat(f.technicalReason()).isEqualTo("EMPTY_INPUT");
    }
}