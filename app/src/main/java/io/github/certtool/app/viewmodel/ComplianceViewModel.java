package io.github.certtool.app.viewmodel;

import io.github.certtool.compliance.loader.DefaultProfiles;
import io.github.certtool.domain.assessment.AssessmentFinding;
import io.github.certtool.domain.assessment.AssessmentReport;
import io.github.certtool.domain.assessment.AssessmentStatus;
import io.github.certtool.domain.assessment.Severity;
import io.github.certtool.domain.profile.Profile;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * View-model for the Compliance page: holds the selected {@link Profile}, the current
 * {@link AssessmentReport}, the filter text + severity, and the filtered findings list shown in
 * the table.
 *
 * <p>Filtering is pure-data: status/severity/rule/alias matchers; no UI logic.
 */
public final class ComplianceViewModel {

    private final ObjectProperty<Profile> selectedProfile = new SimpleObjectProperty<>();
    private final ObjectProperty<AssessmentReport> report = new SimpleObjectProperty<>();
    private final StringProperty filterText = new SimpleStringProperty("");
    private final ObjectProperty<Severity> minSeverityFilter = new SimpleObjectProperty<>(Severity.INFO);
    private final ObservableList<AssessmentFinding> filteredFindings = FXCollections.observableArrayList();
    private final ObservableList<Profile> availableProfiles = FXCollections.observableArrayList();
    private final ObjectProperty<AssessmentFinding> selectedFinding = new SimpleObjectProperty<>();

    public ComplianceViewModel() throws IOException {
        availableProfiles.addAll(DefaultProfiles.all());
        selectedProfile.set(availableProfiles.isEmpty() ? null : availableProfiles.get(0));
    }

    public ObjectProperty<Profile> selectedProfileProperty() { return selectedProfile; }
    public Profile getSelectedProfile() { return selectedProfile.get(); }
    public void setSelectedProfile(Profile p) { selectedProfile.set(p); }

    public ObjectProperty<AssessmentReport> reportProperty() { return report; }
    public AssessmentReport getReport() { return report.get(); }
    public void setReport(AssessmentReport r) {
        selectedFinding.set(null);
        report.set(r);
        rebuildFiltered();
    }

    public ObjectProperty<AssessmentFinding> selectedFindingProperty() { return selectedFinding; }
    public AssessmentFinding getSelectedFinding() { return selectedFinding.get(); }
    public void setSelectedFinding(AssessmentFinding f) { selectedFinding.set(f); }

    public void clearReport() {
        selectedFinding.set(null);
        report.set(null);
        rebuildFiltered();
    }

    public StringProperty filterTextProperty() { return filterText; }
    public String getFilterText() { return filterText.get(); }
    public void setFilterText(String s) {
        filterText.set(s == null ? "" : s);
        rebuildFiltered();
    }

    public ObjectProperty<Severity> minSeverityFilterProperty() { return minSeverityFilter; }
    public Severity getMinSeverityFilter() { return minSeverityFilter.get(); }
    public void setMinSeverityFilter(Severity s) {
        minSeverityFilter.set(s == null ? Severity.INFO : s);
        rebuildFiltered();
    }

    public ObservableList<AssessmentFinding> filteredFindings() { return filteredFindings; }
    public ObservableList<Profile> availableProfiles() { return availableProfiles; }

    /** Per-status counts for the summary cards. Empty map when no report is set. */
    public Map<AssessmentStatus, Long> summaryCounts() {
        AssessmentReport r = report.get();
        return r == null ? Map.of() : r.summary();
    }

    private void rebuildFiltered() {
        AssessmentReport r = report.get();
        filteredFindings.clear();
        if (r == null) {
            return;
        }
        String needle = filterText.get() == null ? "" : filterText.get().toLowerCase(Locale.ROOT);
        Severity min = minSeverityFilter.get();
        for (AssessmentFinding f : r.findings()) {
            if (min != null && f.severity().ordinal() < min.ordinal()) {
                continue;
            }
            if (!needle.isEmpty()) {
                String hay = (f.ruleId() + " " + f.title() + " " + f.summary()).toLowerCase(java.util.Locale.ROOT);
                if (!hay.contains(needle)) {
                    continue;
                }
            }
            filteredFindings.add(f);
        }
    }
}