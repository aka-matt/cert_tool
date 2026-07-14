package io.github.certtool.app.task;

import io.github.certtool.certanalysis.core.CertificateAnalyzer;
import io.github.certtool.domain.certificate.CertificateAnalysis;
import io.github.certtool.domain.inspect.InspectedCertificate;
import io.github.certtool.domain.inspect.InspectedEntry;
import io.github.certtool.domain.inspect.InspectedKeyStore;
import io.github.certtool.domain.inspect.KeyStoreSummary;
import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.load.KeyStoreLoadResult;
import io.github.certtool.domain.load.LoadedEntry;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Background {@link Task} that walks every entry of a successful {@link KeyStoreLoadResult} and
 * parses each certificate via {@link CertificateAnalyzer}.
 *
 * <p>The task is cancellable; cancellation is reported through {@code Worker.State.CANCELLED}.
 * One misbehaving certificate is recorded as a warning on its entry rather than tearing down the
 * whole keystore.
 */
public final class AnalyzeKeyStoreTask extends Task<InspectedKeyStore> {

    private static final Logger LOG = LoggerFactory.getLogger(AnalyzeKeyStoreTask.class);

    private final KeyStoreLoadResult result;
    private final ContentEncoding encoding;

    public AnalyzeKeyStoreTask(KeyStoreLoadResult result, ContentEncoding encoding) {
        this.result = result;
        this.encoding = encoding;
    }

    @Override
    protected InspectedKeyStore call() {
        if (!result.isSuccess()) {
            tryMessage("Skipping analysis: load failed.");
            return null;
        }
        KeyStoreSummary summary = KeyStoreSummary.from(result, encoding);
        int total = result.entries().size();
        List<InspectedEntry> inspected = new ArrayList<>(total);
        int processed = 0;
        for (LoadedEntry entry : result.entries()) {
            if (isCancelled()) {
                return null;
            }
            inspected.add(inspectEntry(entry));
            processed++;
            updateProgress(processed, total);
        }
        tryMessage("Analyzed " + total + " entries.");
        return new InspectedKeyStore(summary, inspected);
    }

    private InspectedEntry inspectEntry(LoadedEntry entry) {
        List<InspectedCertificate> parsed = new ArrayList<>();
        List<String> warnings = new ArrayList<>(entry.warnings());
        int index = 0;
        for (java.security.cert.Certificate cert : entry.certificateChain()) {
            if (isCancelled()) {
                return new InspectedEntry(
                        entry.alias(), entry.entryType(), entry.creationDate(),
                        entry.readable(), entry.keyAlgorithm(), entry.keySize(),
                        parsed, warnings);
            }
            if (cert instanceof X509Certificate x509) {
                try {
                    CertificateAnalysis analysis = CertificateAnalyzer.analyze(x509);
                    parsed.add(new InspectedCertificate(index, analysis));
                } catch (Exception e) {
                    warnings.add("Failed to analyze certificate at index " + index
                            + ": " + e.getClass().getSimpleName());
                    LOG.debug("Analyze failure at index {}: {}", index, e.getClass().getSimpleName());
                }
            } else {
                warnings.add("Skipped non-X.509 certificate at index " + index);
            }
            index++;
        }
        return new InspectedEntry(
                entry.alias(), entry.entryType(), entry.creationDate(),
                entry.readable(), entry.keyAlgorithm(), entry.keySize(),
                parsed, warnings);
    }

    private void tryMessage(String msg) {
        try {
            updateMessage(msg);
        } catch (IllegalStateException ignored) {
            // toolkit not initialised — fine for headless callers
        }
    }
}