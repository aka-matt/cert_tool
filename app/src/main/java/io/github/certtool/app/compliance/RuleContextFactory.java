package io.github.certtool.app.compliance;

import io.github.certtool.domain.certificate.CertificateAnalysis;
import io.github.certtool.domain.context.EntryAnalysis;
import io.github.certtool.domain.context.LoadedKeyStoreInfo;
import io.github.certtool.domain.context.RuntimeEnvironment;
import io.github.certtool.domain.context.RuleContext;
import io.github.certtool.domain.inspect.InspectedCertificate;
import io.github.certtool.domain.inspect.InspectedEntry;
import io.github.certtool.domain.inspect.InspectedKeyStore;
import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.load.KeyStoreLoadResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds a {@link RuleContext} for the assessment engine from the inspected keystore pipeline
 * plus a fresh {@link RuntimeEnvironment} snapshot.
 *
 * <p>This class is pure: no JavaFX dependency, no I/O. All callers must supply a successful
 * {@link KeyStoreLoadResult} and a non-null {@link InspectedKeyStore}; otherwise the factory
 * throws to signal programmer error.
 */
public final class RuleContextFactory {

    private RuleContextFactory() {}

    public static RuleContext from(
            KeyStoreLoadResult load,
            InspectedKeyStore inspected,
            ContentEncoding encoding,
            String sourcePathOrNull,
            long sizeBytes,
            RuntimeEnvironment runtime) {
        Objects.requireNonNull(load, "load");
        Objects.requireNonNull(inspected, "inspected");
        Objects.requireNonNull(encoding, "encoding");
        Objects.requireNonNull(runtime, "runtime");
        if (!load.isSuccess()) {
            throw new IllegalArgumentException("load must be a successful KeyStoreLoadResult");
        }

        List<String> aliases = new ArrayList<>(inspected.entries().size());
        for (InspectedEntry e : inspected.entries()) {
            aliases.add(e.alias());
        }
        LoadedKeyStoreInfo info = new LoadedKeyStoreInfo(
                load.container(),
                encoding,
                sourcePathOrNull,
                sizeBytes,
                true,
                List.copyOf(aliases));

        List<EntryAnalysis> entries = new ArrayList<>(inspected.entries().size());
        for (InspectedEntry e : inspected.entries()) {
            CertificateAnalysis leaf = null;
            if (!e.certificates().isEmpty()) {
                InspectedCertificate c = e.certificates().get(0);
                leaf = c.analysis();
            }
            entries.add(new EntryAnalysis(e.alias(), e.entryType(), leaf, null, null));
        }

        return new RuleContext(info, List.copyOf(entries), runtime);
    }
}