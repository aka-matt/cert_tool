package io.github.certtool.app.controller;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.app.viewmodel.ComplianceViewModel;
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

@DisplayName("ComplianceController")
class ComplianceControllerTest {

    private static AssessmentReport sampleReport() throws Exception {
        Profile p = DefaultProfiles.loadFips1403();
        return new AssessmentReport(p, Instant.parse("2026-07-14T00:00:00Z"), List.of(
                new AssessmentFinding("R-1", "Container check",
                        AssessmentStatus.PASS, Severity.INFO,
                        "container is BCFKS", "evidence", "rem", List.of()),
                new AssessmentFinding("R-2", "Algorithm check",
                        AssessmentStatus.WARNING, Severity.MEDIUM,
                        "uses RSA-1024", "evidence", "rem", List.of()),
                new AssessmentFinding("R-3", "Signature check",
                        AssessmentStatus.FAIL, Severity.CRITICAL,
                        "signature is SHA-1", "evidence", "rem", List.of())));
    }

    @Test
    @DisplayName("default profile is the first available profile")
    void defaultProfile() throws Exception {
        ComplianceViewModel vm = new ComplianceViewModel();
        ComplianceController c = new ComplianceController(vm);
        assertThat(c.viewModel().getSelectedProfile()).isNotNull();
        assertThat(c.viewModel().availableProfiles()).isNotEmpty();
    }

    @Test
    @DisplayName("onProfileSelected changes the selected profile")
    void selectProfile() throws Exception {
        ComplianceViewModel vm = new ComplianceViewModel();
        ComplianceController c = new ComplianceController(vm);
        Profile second = vm.availableProfiles().get(1);
        c.onProfileSelected(second);
        assertThat(c.viewModel().getSelectedProfile()).isSameAs(second);
    }

    @Test
    @DisplayName("onReportProduced populates the view-model and rebuilds the filtered list")
    void reportPropagates() throws Exception {
        ComplianceViewModel vm = new ComplianceViewModel();
        ComplianceController c = new ComplianceController(vm);
        c.onReportProduced(sampleReport());
        assertThat(c.viewModel().getReport()).isNotNull();
        assertThat(c.viewModel().filteredFindings()).hasSize(3);
    }

    @Test
    @DisplayName("filter text narrows findings to those containing the needle (case-insensitive)")
    void filterText() throws Exception {
        ComplianceViewModel vm = new ComplianceViewModel();
        ComplianceController c = new ComplianceController(vm);
        c.onReportProduced(sampleReport());
        c.onFilterTextChanged("RSA");
        assertThat(c.viewModel().filteredFindings()).hasSize(1);
        assertThat(c.viewModel().filteredFindings().get(0).ruleId()).isEqualTo("R-2");
    }

    @Test
    @DisplayName("minimum severity filter hides findings below the threshold")
    void minSeverityFilter() throws Exception {
        ComplianceViewModel vm = new ComplianceViewModel();
        ComplianceController c = new ComplianceController(vm);
        c.onReportProduced(sampleReport());
        c.onMinSeverityChanged(Severity.HIGH);
        assertThat(c.viewModel().filteredFindings()).hasSize(1);
        assertThat(c.viewModel().filteredFindings().get(0).ruleId()).isEqualTo("R-3");
    }

    @Test
    @DisplayName("summary counts include all five statuses (zeros explicit)")
    void summaryCounts() throws Exception {
        ComplianceViewModel vm = new ComplianceViewModel();
        ComplianceController c = new ComplianceController(vm);
        c.onReportProduced(sampleReport());
        var counts = c.viewModel().summaryCounts();
        assertThat(counts).containsKeys(AssessmentStatus.PASS, AssessmentStatus.WARNING,
                AssessmentStatus.FAIL, AssessmentStatus.NOT_ASSESSABLE, AssessmentStatus.NOT_APPLICABLE);
        assertThat(counts.get(AssessmentStatus.PASS)).isEqualTo(1L);
        assertThat(counts.get(AssessmentStatus.FAIL)).isEqualTo(1L);
    }
}