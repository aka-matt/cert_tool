package io.github.certtool.app.controller;

import io.github.certtool.app.viewmodel.ComplianceViewModel;
import io.github.certtool.domain.assessment.AssessmentReport;
import io.github.certtool.domain.profile.Profile;
import java.util.Objects;

/**
 * Pure logic for the Compliance view. Holds no JavaFX references; the view binds to the
 * {@link ComplianceViewModel} this controller mutates.
 *
 * <p>Testable: construct with a fresh {@code ComplianceViewModel} and exercise selection +
 * filtering without instantiating JavaFX controls.
 */
public final class ComplianceController {

    private final ComplianceViewModel viewModel;

    public ComplianceController(ComplianceViewModel viewModel) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
    }

    public void onProfileSelected(Profile profile) {
        viewModel.setSelectedProfile(profile);
    }

    public void onReportProduced(AssessmentReport report) {
        viewModel.setReport(report);
    }

    public void onFilterTextChanged(String text) {
        viewModel.setFilterText(text);
    }

    public void onMinSeverityChanged(io.github.certtool.domain.assessment.Severity s) {
        viewModel.setMinSeverityFilter(s);
    }

    public ComplianceViewModel viewModel() { return viewModel; }
}