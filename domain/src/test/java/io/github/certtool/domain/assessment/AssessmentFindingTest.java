package io.github.certtool.domain.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AssessmentFinding")
class AssessmentFindingTest {

    @Test
    @DisplayName("accepts a complete finding")
    void completeFinding() {
        AssessmentFinding f =
                new AssessmentFinding(
                        "FIPS1403.RSA.MIN_KEY_SIZE",
                        "RSA key size must be at least 2048 bits",
                        AssessmentStatus.FAIL,
                        Severity.HIGH,
                        "Found an RSA key of 1024 bits.",
                        "Alias 'legacy': modulus length 1024.",
                        "Replace the key with at least 2048 bits.",
                        List.of("FIPS 140-3 IG 7.5", "NIST SP 800-131A"));

        assertThat(f.ruleId()).isEqualTo("FIPS1403.RSA.MIN_KEY_SIZE");
        assertThat(f.status()).isEqualTo(AssessmentStatus.FAIL);
        assertThat(f.severity()).isEqualTo(Severity.HIGH);
        assertThat(f.references()).hasSize(2);
    }

    @Test
    @DisplayName("null references list becomes an empty immutable list")
    void nullReferencesBecomeEmpty() {
        AssessmentFinding f =
                new AssessmentFinding(
                        "x",
                        "y",
                        AssessmentStatus.PASS,
                        Severity.INFO,
                        "s",
                        "e",
                        "r",
                        null);

        assertThat(f.references()).isEmpty();
        assertThatThrownBy(() -> f.references().add("nope"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("rejects null rule id")
    void rejectsNullRuleId() {
        assertThatThrownBy(
                        () ->
                                new AssessmentFinding(
                                        null,
                                        "t",
                                        AssessmentStatus.PASS,
                                        Severity.INFO,
                                        "s",
                                        "e",
                                        "r",
                                        List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("ruleId");
    }
}