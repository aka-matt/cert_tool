package io.github.certtool.app.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.compliance.loader.DefaultProfiles;
import io.github.certtool.domain.assessment.AssessmentFinding;
import io.github.certtool.domain.assessment.AssessmentReport;
import io.github.certtool.domain.assessment.AssessmentStatus;
import io.github.certtool.domain.assessment.Severity;
import io.github.certtool.domain.profile.Profile;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ComplianceViewModel")
class ComplianceViewModelTest {

    private static AssessmentReport sampleReport() throws Exception {
        Profile p = DefaultProfiles.loadFips1403();
        return new AssessmentReport(p, Instant.parse("2026-07-15T00:00:00Z"), List.of(
                new AssessmentFinding("R-1", "Pass",
                        AssessmentStatus.PASS, Severity.INFO,
                        "ok", "ev", "rem", List.of()),
                new AssessmentFinding("R-2", "Warn",
                        AssessmentStatus.WARNING, Severity.MEDIUM,
                        "warn", "ev", "rem", List.of())));
    }

    @Test
    @DisplayName("selectedFinding property round-trips and starts null")
    void selectedFindingRoundTrip() throws Exception {
        ComplianceViewModel vm = new ComplianceViewModel();
        assertThat(vm.getSelectedFinding()).isNull();

        AssessmentReport r = sampleReport();
        vm.setReport(r);
        AssessmentFinding f = r.findings().get(0);
        vm.setSelectedFinding(f);

        assertThat(vm.getSelectedFinding()).isSameAs(f);
    }

    @Test
    @DisplayName("clearReport() resets report, filtered list, and selectedFinding")
    void clearReportResetsEverything() throws Exception {
        ComplianceViewModel vm = new ComplianceViewModel();
        vm.setReport(sampleReport());
        vm.setSelectedFinding(sampleReport().findings().get(0));

        vm.clearReport();

        assertThat(vm.getReport()).isNull();
        assertThat(vm.filteredFindings()).isEmpty();
        assertThat(vm.getSelectedFinding()).isNull();
        assertThat(vm.summaryCounts()).isEmpty();
    }
}
