package io.github.certtool.app.task;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.domain.inspect.InspectedEntry;
import io.github.certtool.domain.inspect.InspectedKeyStore;
import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.keystore.EntryType;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.domain.load.KeyStoreLoadResult;
import io.github.certtool.domain.load.LoadedEntry;
import io.github.certtool.testfixtures.CertificateChains;
import io.github.certtool.testfixtures.CertificateGenerator;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.security.auth.x500.X500Principal;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AnalyzeKeyStoreTask")
class AnalyzeKeyStoreTaskTest {

    @BeforeAll
    static void initFx() {
        // Task.cancel() and updateProgress() both route through Platform.runLater; that
        // requires the FX toolkit to be initialised. Headless init is safe here.
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException ignored) {
            // toolkit already started by another test class in the same JVM
        }
    }

    @Test
    @DisplayName("analyzes every certificate in every entry and builds a summary")
    void producesInspection() throws Exception {
        CertificateChains.Chain3 chain = CertificateChains.rootIntermediateLeaf();
        LoadedEntry leaf = new LoadedEntry(
                "leaf", EntryType.PRIVATE_KEY, new Date(),
                List.of(chain.leaf(), chain.intermediate(), chain.root()),
                "RSA", 2048, true, List.of());
        X509Certificate trust = CertificateGenerator.selfSigned(
                new X500Principal("CN=trust"), CertificateGenerator.rsaKeyPair(2048),
                "SHA256withRSA", Duration.ofDays(7));
        LoadedEntry trustEntry = LoadedEntry.trustedCertificate("trust", trust, new Date());
        KeyStoreLoadResult result = KeyStoreLoadResult.success(
                KeyStoreContainerType.BCFKS, "BCFIPS", "1.0",
                List.of(leaf, trustEntry));

        AnalyzeKeyStoreTask task = new AnalyzeKeyStoreTask(result, ContentEncoding.BINARY);
        InspectedKeyStore value = task.call();

        assertThat(value).isNotNull();
        assertThat(value.summary().totalEntries()).isEqualTo(2);
        assertThat(value.summary().totalCertificates()).isEqualTo(4);
        InspectedEntry leaves = value.entries().get(0);
        assertThat(leaves.certificates()).hasSize(3);
        assertThat(leaves.certificates().get(0).analysis().subject()).contains("leaf");
    }

    @Test
    @DisplayName("cancellation leaves getValue() null and reports CANCELLED state")
    void cancelLeavesNoValue() throws Exception {
        X509Certificate trust = CertificateGenerator.selfSigned(
                new X500Principal("CN=trust"), CertificateGenerator.rsaKeyPair(2048),
                "SHA256withRSA", Duration.ofDays(7));
        LoadedEntry trustEntry = LoadedEntry.trustedCertificate("trust", trust, new Date());
        KeyStoreLoadResult result = KeyStoreLoadResult.success(
                KeyStoreContainerType.JKS, "SUN", "17", List.of(trustEntry));

        AnalyzeKeyStoreTask task = new AnalyzeKeyStoreTask(result, ContentEncoding.BINARY);
        task.cancel();
        InspectedKeyStore value = task.call();

        assertThat(value).isNull();
        assertThat(task.isCancelled()).isTrue();
        // stateProperty() is only advanced to CANCELLED when the task is run on a worker
        // (run/schedule); a direct .call() invocation leaves it at READY. Asserting the
        // .isCancelled() flag is the API-correct way to verify cancellation was honored.
    }

    @Test
    @DisplayName("per-certificate failure in one entry does not block other entries")
    void perCertFailureIsolated() throws Exception {
        X509Certificate good = CertificateGenerator.selfSigned(
                new X500Principal("CN=good"), CertificateGenerator.rsaKeyPair(2048),
                "SHA256withRSA", Duration.ofDays(7));
        // Cert whose getEncoded() throws to simulate parser failure.
        X509Certificate bad = new BreakingCert();
        LoadedEntry goodEntry = LoadedEntry.trustedCertificate("good", good, new Date());
        LoadedEntry badEntry = LoadedEntry.trustedCertificate("bad", bad, new Date());
        KeyStoreLoadResult result = KeyStoreLoadResult.success(
                KeyStoreContainerType.JKS, "SUN", "17", List.of(goodEntry, badEntry));

        AnalyzeKeyStoreTask task = new AnalyzeKeyStoreTask(result, ContentEncoding.BINARY);
        task.run();
        assertTaskSucceeded(task);
        InspectedKeyStore value = task.get();

        assertThat(value).isNotNull();
        assertThat(value.entries()).hasSize(2);

        InspectedEntry goodInspected = value.entries().stream()
                .filter(e -> "good".equals(e.alias()))
                .findFirst().orElseThrow();
        assertThat(goodInspected.certificates()).hasSize(1);

        InspectedEntry badInspected = value.entries().stream()
                .filter(e -> "bad".equals(e.alias()))
                .findFirst().orElseThrow();
        assertThat(badInspected.certificates()).isEmpty();
        assertThat(badInspected.warnings()).isNotEmpty();
    }

    private static void assertTaskSucceeded(AnalyzeKeyStoreTask task) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<AssertionError> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                assertThat(task.getException()).isNull();
                assertThat(task.stateProperty().get()).isEqualTo(Worker.State.SUCCEEDED);
            } catch (AssertionError error) {
                failure.set(error);
            } finally {
                latch.countDown();
            }
        });
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        if (failure.get() != null) {
            throw failure.get();
        }
    }

    /**
     * Minimal X.509 certificate stub whose methods throw to simulate parser failure. Extends
     * {@link X509Certificate} so the task's {@code instanceof} branch routes it through
     * {@code CertificateAnalyzer.analyze}; the analyzer then throws and our catch block
     * records the entry as a warning-only.
     */
    private static final class BreakingCert extends X509Certificate {
        private static final long serialVersionUID = 1L;

        BreakingCert() {
            super();
        }

        @Override
        public byte[] getEncoded() throws CertificateEncodingException {
            throw new CertificateEncodingException("simulated parser failure");
        }

        @Override
        public void verify(PublicKey key) throws CertificateException, NoSuchAlgorithmException,
                InvalidKeyException, NoSuchProviderException, SignatureException {
            throw new CertificateException("simulated parser failure");
        }

        @Override
        public void verify(PublicKey key, String sigProvider) throws CertificateException,
                NoSuchAlgorithmException, InvalidKeyException, NoSuchProviderException,
                SignatureException {
            throw new CertificateException("simulated parser failure");
        }

        @Override
        public String toString() {
            return "BreakingCert";
        }

        @Override
        public PublicKey getPublicKey() {
            throw new UnsupportedOperationException("simulated parser failure");
        }

        @Override
        public void checkValidity() throws CertificateExpiredException, CertificateNotYetValidException {
            throw new CertificateExpiredException("simulated parser failure");
        }

        @Override
        public void checkValidity(Date date) throws CertificateExpiredException,
                CertificateNotYetValidException {
            throw new CertificateExpiredException("simulated parser failure");
        }

        @Override
        public javax.security.auth.x500.X500Principal getIssuerX500Principal() {
            throw new UnsupportedOperationException("simulated parser failure");
        }

        @Override
        public javax.security.auth.x500.X500Principal getSubjectX500Principal() {
            throw new UnsupportedOperationException("simulated parser failure");
        }

        @Override
        public java.security.Principal getIssuerDN() {
            throw new UnsupportedOperationException("simulated parser failure");
        }

        @Override
        public java.security.Principal getSubjectDN() {
            throw new UnsupportedOperationException("simulated parser failure");
        }

        @Override
        public Date getNotBefore() {
            throw new UnsupportedOperationException("simulated parser failure");
        }

        @Override
        public Date getNotAfter() {
            throw new UnsupportedOperationException("simulated parser failure");
        }

        @Override
        public String getSigAlgName() {
            throw new UnsupportedOperationException("simulated parser failure");
        }

        @Override
        public String getSigAlgOID() {
            throw new UnsupportedOperationException("simulated parser failure");
        }

        @Override
        public byte[] getSigAlgParams() {
            throw new UnsupportedOperationException("simulated parser failure");
        }

        @Override
        public boolean[] getIssuerUniqueID() {
            throw new UnsupportedOperationException("simulated parser failure");
        }

        @Override
        public boolean[] getSubjectUniqueID() {
            throw new UnsupportedOperationException("simulated parser failure");
        }

        @Override
        public boolean[] getKeyUsage() {
            throw new UnsupportedOperationException("simulated parser failure");
        }

        @Override
        public int getBasicConstraints() {
            throw new UnsupportedOperationException("simulated parser failure");
        }

        @Override
        public byte[] getSignature() {
            throw new UnsupportedOperationException("simulated parser failure");
        }

        @Override
        public byte[] getTBSCertificate() throws CertificateEncodingException {
            throw new CertificateEncodingException("simulated parser failure");
        }

        @Override
        public java.math.BigInteger getSerialNumber() {
            throw new UnsupportedOperationException("simulated parser failure");
        }

        @Override
        public int getVersion() {
            throw new UnsupportedOperationException("simulated parser failure");
        }

        @Override
        public boolean hasUnsupportedCriticalExtension() {
            throw new UnsupportedOperationException("simulated parser failure");
        }

        @Override
        public Set<String> getCriticalExtensionOIDs() {
            throw new UnsupportedOperationException("simulated parser failure");
        }

        @Override
        public Set<String> getNonCriticalExtensionOIDs() {
            throw new UnsupportedOperationException("simulated parser failure");
        }

        @Override
        public byte[] getExtensionValue(String oid) {
            throw new UnsupportedOperationException("simulated parser failure");
        }
    }
}