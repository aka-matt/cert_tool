package io.github.certtool.app.viewmodel;

import io.github.certtool.domain.context.ProviderInfo;
import io.github.certtool.domain.context.RuntimeEnvironment;
import java.util.List;
import java.util.function.Function;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * View-model for the Runtime page. Holds the captured {@link RuntimeEnvironment} plus derived
 * flags for FIPS / Approved-Only detection the page surfaces. Read-only access via {@link
 * #getEnvironment()} — mutation goes through {@link RuntimeController#refresh()}.
 */
public final class RuntimeViewModel {

    private final ObjectProperty<RuntimeEnvironment> environment = new SimpleObjectProperty<>();
    private final ObservableList<ProviderInfo> providers = FXCollections.observableArrayList();

    public ObjectProperty<RuntimeEnvironment> environmentProperty() { return environment; }
    public RuntimeEnvironment getEnvironment() { return environment.get(); }
    public void setEnvironment(RuntimeEnvironment e) {
        environment.set(e);
        providers.setAll(e == null ? List.of() : e.providers());
    }

    public ObservableList<ProviderInfo> providers() { return providers; }

    public ReadOnlyStringWrapper jvmVendorProperty() {
        return derivedString(env -> env == null ? "" : env.jvmVendor());
    }
    public String getJvmVendor() { return jvmVendorProperty().get(); }

    public ReadOnlyStringWrapper jvmVersionProperty() {
        return derivedString(env -> env == null ? "" : env.jvmVersion());
    }
    public String getJvmVersion() { return jvmVersionProperty().get(); }

    /** True iff any installed provider self-identifies as BCFIPS. */
    public boolean bcfipsDetected() {
        RuntimeEnvironment env = environment.get();
        return env != null && env.bcfipsDetected();
    }

    /** True iff any installed provider has confirmed Approved-Only Mode. */
    public boolean approvedOnlyConfirmed() {
        RuntimeEnvironment env = environment.get();
        return env != null && env.approvedOnlyConfirmed();
    }

    /** Returns each provider formatted as "name:version" for display. */
    public List<String> providerVersions() {
        return providers.stream().map(p -> p.name() + ":" + p.version()).toList();
    }

    private ReadOnlyStringWrapper derivedString(Function<RuntimeEnvironment, String> f) {
        ReadOnlyStringWrapper w = new ReadOnlyStringWrapper(f.apply(environment.get()));
        environment.addListener((o, oldE, newE) -> w.set(f.apply(newE)));
        return w;
    }
}
