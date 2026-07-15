package io.github.certtool.app.viewmodel;

import io.github.certtool.domain.inspect.InspectedEntry;
import io.github.certtool.domain.inspect.InspectedKeyStore;
import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.keystore.EntryType;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.domain.load.KeyStoreLoadResult;
import io.github.certtool.domain.load.LoadedEntry;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * View-model for the Inspect view: holds the currently-loaded keystore result, the selected alias,
 * and computed groupings for the left-nav TreeView.
 *
 * <p>This is the only place that exposes JavaFX observable properties for the Inspect view. Views
 * observe these properties; controllers mutate them. No business logic lives in the view-model —
 * it only mirrors and groups loader output.
 */
public final class InspectViewModel {

    /** Group categories for the left-nav TreeView. */
    public enum Group {
        PRIVATE_KEYS,
        TRUSTED_CERTIFICATES,
        SECRET_KEYS,
        UNREADABLE_ENTRIES
    }

    /** Immutable view of one left-nav node. */
    public record NavNode(Group group, String alias, EntryType entryType, String status) {}

    private final ObjectProperty<KeyStoreLoadResult> loadResult = new SimpleObjectProperty<>();
    private final StringProperty selectedAlias = new SimpleStringProperty();
    private final ObservableList<NavNode> navNodes = FXCollections.observableArrayList();
    private final ObjectProperty<InspectedKeyStore> inspected = new SimpleObjectProperty<>();
    private final ReadOnlyObjectWrapper<InspectedEntry> currentEntry = new ReadOnlyObjectWrapper<>();
    private final IntegerProperty currentCertificateIndex = new SimpleIntegerProperty(0);
    private final ObjectProperty<ContentEncoding> contentEncoding = new SimpleObjectProperty<>();
    private final StringProperty sourcePath = new SimpleStringProperty("");

    {
        selectedAliasProperty().addListener((obs, oldV, newV) -> {
            recomputeCurrentEntry();
            currentCertificateIndex.set(0);
        });
        inspected.addListener((obs, oldV, newV) -> {
            if (newV != null && getSelectedAlias() == null && !newV.entries().isEmpty()) {
                setSelectedAlias(newV.entries().get(0).alias());
            }
            recomputeCurrentEntry();
            currentCertificateIndex.set(0);
        });
    }

    public ObjectProperty<KeyStoreLoadResult> loadResultProperty() { return loadResult; }
    public KeyStoreLoadResult getLoadResult() { return loadResult.get(); }
    public void setLoadResult(KeyStoreLoadResult r) {
        loadResult.set(r);
        rebuildNav();
        // Auto-select first node if nothing was selected.
        if (selectedAlias.get() == null && !navNodes.isEmpty()) {
            selectedAlias.set(navNodes.get(0).alias());
        }
    }

    public StringProperty selectedAliasProperty() { return selectedAlias; }
    public String getSelectedAlias() { return selectedAlias.get(); }
    public void setSelectedAlias(String alias) { selectedAlias.set(alias); }

    public ObservableList<NavNode> navNodes() { return navNodes; }

    /** Returns the {@link LoadedEntry} for the currently-selected alias, or empty. */
    public Optional<LoadedEntry> selectedEntry() {
        KeyStoreLoadResult r = loadResult.get();
        String alias = selectedAlias.get();
        if (r == null || alias == null) {
            return Optional.empty();
        }
        for (LoadedEntry e : r.entries()) {
            if (Objects.equals(e.alias(), alias)) {
                return Optional.of(e);
            }
        }
        return Optional.empty();
    }

    /** Returns the first certificate chain for the selected entry, or empty. */
    public Optional<List<Certificate>> selectedChain() {
        return selectedEntry().map(LoadedEntry::certificateChain);
    }

    /**
     * Returns the loaded container type, or null when nothing is loaded.
     */
    public KeyStoreContainerType containerType() {
        KeyStoreLoadResult r = loadResult.get();
        return r == null ? null : r.container();
    }

    /** Rebuilds the {@link #navNodes} list from the current load result. */
    private void rebuildNav() {
        navNodes.clear();
        KeyStoreLoadResult r = loadResult.get();
        if (r == null || !r.isSuccess()) {
            return;
        }
        // Group entries by EntryType into ordered buckets. TreeView builder renders these.
        Map<Group, List<NavNode>> buckets = new LinkedHashMap<>();
        buckets.put(Group.PRIVATE_KEYS, new ArrayList<>());
        buckets.put(Group.TRUSTED_CERTIFICATES, new ArrayList<>());
        buckets.put(Group.SECRET_KEYS, new ArrayList<>());
        buckets.put(Group.UNREADABLE_ENTRIES, new ArrayList<>());

        for (LoadedEntry e : r.entries()) {
            Group g = groupFor(e);
            buckets.get(g).add(new NavNode(g, e.alias(), e.entryType(), "—"));
        }
        for (List<NavNode> bucket : buckets.values()) {
            Collections.sort(bucket, (a, b) -> a.alias().compareTo(b.alias()));
        }
        navNodes.addAll(buckets.get(Group.PRIVATE_KEYS));
        navNodes.addAll(buckets.get(Group.TRUSTED_CERTIFICATES));
        navNodes.addAll(buckets.get(Group.SECRET_KEYS));
        navNodes.addAll(buckets.get(Group.UNREADABLE_ENTRIES));
    }

    private static Group groupFor(LoadedEntry e) {
        return switch (e.entryType()) {
            case PRIVATE_KEY -> Group.PRIVATE_KEYS;
            case TRUSTED_CERTIFICATE -> Group.TRUSTED_CERTIFICATES;
            case SECRET_KEY -> Group.SECRET_KEYS;
            case UNKNOWN -> Group.UNREADABLE_ENTRIES;
        };
    }

    public ObjectProperty<InspectedKeyStore> inspectedProperty() { return inspected; }
    public InspectedKeyStore getInspected() { return inspected.get(); }
    public void setInspected(InspectedKeyStore v) { inspected.set(v); }

    public ReadOnlyObjectProperty<InspectedEntry> currentEntryProperty() {
        return currentEntry.getReadOnlyProperty();
    }
    public InspectedEntry getCurrentEntry() { return currentEntry.get(); }

    public IntegerProperty currentCertificateIndexProperty() { return currentCertificateIndex; }
    public int getCurrentCertificateIndex() { return currentCertificateIndex.get(); }
    public void incrementCurrentCertificateIndex() {
        currentCertificateIndex.set(currentCertificateIndex.get() + 1);
    }
    public void decrementCurrentCertificateIndex() {
        int next = currentCertificateIndex.get() - 1;
        currentCertificateIndex.set(Math.max(0, next));
    }

    public ObjectProperty<ContentEncoding> contentEncodingProperty() { return contentEncoding; }
    public ContentEncoding getContentEncoding() { return contentEncoding.get(); }
    public void setContentEncoding(ContentEncoding e) { contentEncoding.set(e); }

    public StringProperty sourcePathProperty() { return sourcePath; }
    public String getSourcePath() {
        String v = sourcePath.get();
        return v == null ? "" : v;
    }
    public void setSourcePath(String p) { sourcePath.set(p == null ? "" : p); }

    private void recomputeCurrentEntry() {
        InspectedKeyStore s = inspected.get();
        String alias = getSelectedAlias();
        if (s == null || alias == null) {
            currentEntry.set(null);
            return;
        }
        for (InspectedEntry e : s.entries()) {
            if (alias.equals(e.alias())) {
                currentEntry.set(e);
                return;
            }
        }
        currentEntry.set(null);
    }
}