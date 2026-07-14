package io.github.certtool.app.viewmodel;

import io.github.certtool.conversion.domain.plan.AliasConflictPolicy;
import io.github.certtool.conversion.domain.plan.OverwritePolicy;
import io.github.certtool.conversion.domain.preflight.PreflightReport;
import io.github.certtool.conversion.domain.result.ConversionResult;
import io.github.certtool.domain.context.LoadedKeyStoreInfo;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * View-model for the Convert wizard. Holds the loaded source keystore summary, the chosen
 * target path, the per-entry alias selection list, the wizard's
 * {@link AliasConflictPolicy}/{@link OverwritePolicy}, and the last preflight + execution
 * reports so the wizard can show them on steps 4 and 5.
 */
public final class ConvertViewModel {

    private final ObjectProperty<LoadedKeyStoreInfo> source = new SimpleObjectProperty<>();
    private final StringProperty sourcePath = new SimpleStringProperty("");
    private final StringProperty targetPath = new SimpleStringProperty("");
    private final ObjectProperty<AliasConflictPolicy> aliasConflictPolicy =
            new SimpleObjectProperty<>(AliasConflictPolicy.RENAME);
    private final ObjectProperty<OverwritePolicy> overwritePolicy =
            new SimpleObjectProperty<>(OverwritePolicy.FAIL_IF_EXISTS);
    /** Preserves the source alias order so re-added aliases return to their original position. */
    private final ObservableList<String> sourceAliases = FXCollections.observableArrayList();
    private final ObservableList<String> selectedAliases = FXCollections.observableArrayList();
    private final ObjectProperty<PreflightReport> preflightReport = new SimpleObjectProperty<>();
    private final ObjectProperty<ConversionResult> lastResult = new SimpleObjectProperty<>();

    public ObjectProperty<LoadedKeyStoreInfo> sourceProperty() { return source; }
    public LoadedKeyStoreInfo getSource() { return source.get(); }
    public void setSource(LoadedKeyStoreInfo value) { source.set(value); }

    public StringProperty sourcePathProperty() { return sourcePath; }
    public String getSourcePath() { return sourcePath.get(); }
    public void setSourcePath(String p) { sourcePath.set(p == null ? "" : p); }

    public StringProperty targetPathProperty() { return targetPath; }
    public String getTargetPath() { return targetPath.get(); }
    public void setTargetPath(String p) { targetPath.set(p == null ? "" : p); }

    public ObjectProperty<AliasConflictPolicy> aliasConflictPolicyProperty() { return aliasConflictPolicy; }
    public AliasConflictPolicy getAliasConflictPolicy() { return aliasConflictPolicy.get(); }
    public void setAliasConflictPolicy(AliasConflictPolicy p) { aliasConflictPolicy.set(p); }

    public ObjectProperty<OverwritePolicy> overwritePolicyProperty() { return overwritePolicy; }
    public OverwritePolicy getOverwritePolicy() { return overwritePolicy.get(); }
    public void setOverwritePolicy(OverwritePolicy p) { overwritePolicy.set(p); }

    public ObservableList<String> selectedAliases() { return selectedAliases; }
    public ObservableList<String> sourceAliases() { return sourceAliases; }

    public ObjectProperty<PreflightReport> preflightReportProperty() { return preflightReport; }
    public PreflightReport getPreflightReport() { return preflightReport.get(); }
    public void setPreflightReport(PreflightReport r) { preflightReport.set(r); }

    public ObjectProperty<ConversionResult> lastResultProperty() { return lastResult; }
    public ConversionResult getLastResult() { return lastResult.get(); }
    public void setLastResult(ConversionResult r) { lastResult.set(r); }
}
