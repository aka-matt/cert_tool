package io.github.certtool.app.controller;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.certtool.app.AppComposition;
import io.github.certtool.app.settings.Settings;
import io.github.certtool.app.settings.SettingsService;
import io.github.certtool.app.task.AnalyzeKeyStoreTask;
import io.github.certtool.app.task.AssessmentTask;
import io.github.certtool.app.theme.ThemeMode;
import io.github.certtool.app.theme.ThemeService;
import io.github.certtool.app.viewmodel.ComplianceViewModel;
import io.github.certtool.app.viewmodel.ConvertViewModel;
import io.github.certtool.app.viewmodel.InspectViewModel;
import io.github.certtool.app.viewmodel.RuntimeViewModel;
import io.github.certtool.compliance.core.AssessmentEngine;
import io.github.certtool.compliance.core.DefaultRules;
import io.github.certtool.domain.assessment.AssessmentReport;
import io.github.certtool.domain.certificate.BasicConstraintsInfo;
import io.github.certtool.domain.certificate.CertificateAnalysis;
import io.github.certtool.domain.certificate.ExtensionAnalysis;
import io.github.certtool.domain.certificate.FingerprintBundle;
import io.github.certtool.domain.certificate.KeyAlgorithm;
import io.github.certtool.domain.certificate.KeyUsageBits;
import io.github.certtool.domain.certificate.PublicKeyInfo;
import io.github.certtool.domain.certificate.SelfSignedStatus;
import io.github.certtool.domain.certificate.ValidityState;
import io.github.certtool.domain.certificate.ValidityWindow;
import io.github.certtool.domain.error.LoadFailure;
import io.github.certtool.domain.error.LoadFailureReason;
import io.github.certtool.domain.inspect.InspectedCertificate;
import io.github.certtool.domain.inspect.InspectedEntry;
import io.github.certtool.domain.inspect.InspectedKeyStore;
import io.github.certtool.domain.inspect.KeyStoreSummary;
import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.keystore.EntryType;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.domain.load.KeyStoreLoadResult;
import io.github.certtool.domain.load.LoadedEntry;
import io.github.certtool.keystorecore.load.KeyStoreLoader;
import io.github.certtool.keystorecore.password.FixedPasswordProvider;
import io.github.certtool.testfixtures.CertificateGenerator;
import io.github.certtool.testfixtures.KeyStoreGenerator;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

@DisplayName("MainShellController")
class MainShellControllerTest {

    @BeforeAll
    static void initFx() {
        // Inspect view construction instantiates JavaFX controls (TreeView, TabPane, ...);
        // those need the FX toolkit to be initialised even for headless callers.
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException ignored) {
            // toolkit already started by another test class in the same JVM
        }
    }

    @Test
    @DisplayName("composition auto-detects a BCFKS truststore without a filename container hint")
    void compositionAutoDetectsBcfksTruststore() throws Exception {
        java.security.cert.X509Certificate certificate = CertificateGenerator.selfSigned(
                new javax.security.auth.x500.X500Principal("CN=composition-trust"),
                CertificateGenerator.rsaKeyPair(2048),
                "SHA256withRSA",
                java.time.Duration.ofDays(7));
        char[] password = new char[0];
        byte[] bytes = KeyStoreGenerator.toBytes(KeyStoreGenerator.bcfks(password, "trust", certificate), password);

        var task = composition(new RecordingExecutor()).autoDetectLoadTask(bytes);
        task.run();

        assertThat(task.get().container()).isEqualTo(KeyStoreContainerType.BCFKS);
    }

    @Test
    @DisplayName("selected-file task detects BCFKS truststore named with a truststore suffix")
    void selectedFileTaskDetectsBcfksTruststoreNamedWithTruststoreSuffix() throws Exception {
        java.security.cert.X509Certificate certificate = CertificateGenerator.selfSigned(
                new javax.security.auth.x500.X500Principal("CN=selected-file-trust"),
                CertificateGenerator.rsaKeyPair(2048),
                "SHA256withRSA",
                java.time.Duration.ofDays(7));
        char[] password = new char[0];
        byte[] bytes = KeyStoreGenerator.toBytes(KeyStoreGenerator.bcfks(password, "trust", certificate), password);
        Path store = Files.createTempFile("cert-tool", ".truststore");
        try {
            Files.write(store, bytes);

            var task = composition(new RecordingExecutor()).autoDetectLoadTask(store);
            task.run();

            KeyStoreLoadResult result = task.get();

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.container()).isEqualTo(KeyStoreContainerType.BCFKS);
            assertThat(result.entries())
                    .singleElement()
                    .extracting(LoadedEntry::entryType)
                    .isEqualTo(EntryType.TRUSTED_CERTIFICATE);
        } finally {
            Files.deleteIfExists(store);
        }
    }

    @Test
    @DisplayName("selected-file task detects PKCS12 truststore named with a p12 suffix")
    void selectedFileTaskDetectsPkcs12TruststoreNamedWithP12Suffix() throws Exception {
        java.security.cert.X509Certificate certificate = CertificateGenerator.selfSigned(
                new javax.security.auth.x500.X500Principal("CN=selected-file-pkcs12-trust"),
                CertificateGenerator.rsaKeyPair(2048),
                "SHA256withRSA",
                java.time.Duration.ofDays(7));
        char[] password = new char[0];
        byte[] bytes = KeyStoreGenerator.toBytes(KeyStoreGenerator.pkcs12(password, "trust", certificate), password);
        Path store = Files.createTempFile("cert-tool", ".p12");
        try {
            Files.write(store, bytes);

            var task = composition(new RecordingExecutor()).autoDetectLoadTask(store);
            task.run();

            KeyStoreLoadResult result = task.get();

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.container()).isEqualTo(KeyStoreContainerType.PKCS12);
            assertThat(result.entries())
                    .singleElement()
                    .extracting(LoadedEntry::entryType)
                    .isEqualTo(EntryType.TRUSTED_CERTIFICATE);
        } finally {
            Files.deleteIfExists(store);
        }
    }

    @Test
    @DisplayName("blank pasted Base64 does not submit a load task")
    void blankPasteDoesNotSubmitALoadTask() throws Exception {
        RecordingExecutor executor = new RecordingExecutor();
        MainShellController controller = new MainShellController(composition(executor), null);

        controller.handlePastedBase64("   ");

        assertThat(executor.submittedTasks()).isEmpty();
    }

    @Test
    @DisplayName("pasted Base64 is not logged when the load task is submitted")
    void pasteActionDoesNotLogTheSubmittedBase64() throws Exception {
        RecordingExecutor executor = new RecordingExecutor();
        MainShellController controller = new MainShellController(composition(executor), null);
        String pastedBase64 = "c2VjcmV0LXBheWxvYWQ=";
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(MainShellController.class);
        ListAppender<ILoggingEvent> logCapture = new ListAppender<>();
        logCapture.start();
        logger.addAppender(logCapture);
        try {
            controller.handlePastedBase64(pastedBase64);
        } finally {
            logger.detachAppender(logCapture);
            logCapture.stop();
        }

        assertThat(logCapture.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .doesNotContain(pastedBase64);
        assertThat(executor.submittedTasks()).hasSize(1);
    }

    @Test
    @DisplayName("unique pasted result is handed to Inspect without a selection or retry")
    void uniquePastedResultIsHandedToInspect() throws Exception {
        RecordingExecutor executor = new RecordingExecutor();
        AppComposition composition = composition(executor);
        MainShellController controller = new MainShellController(composition, null);
        AtomicInteger selections = new AtomicInteger();
        KeyStoreLoadResult success = KeyStoreLoadResult.success(KeyStoreContainerType.JKS, "test", "1", List.of());

        controller.handlePastedLoadResult("secret", success, () -> {
            selections.incrementAndGet();
            return Optional.of(KeyStoreContainerType.BCFKS);
        });

        assertThat(composition.inspectVm().getLoadResult()).isNotNull();
        assertThat(composition.inspectVm().getLoadResult().container()).isEqualTo(KeyStoreContainerType.JKS);
        assertThat(selections).hasValue(0);
        // Success branch now chains exactly one analyze task (no selection, no retry load).
        assertThat(executor.submittedTasks()).hasSize(1);
    }

    @Test
    @DisplayName("no matching container does not open selection or submit a retry")
    void noMatchingContainerDoesNotOpenSelectionOrRetry() throws Exception {
        RecordingExecutor executor = new RecordingExecutor();
        MainShellController controller = new MainShellController(composition(executor), null);
        AtomicInteger selections = new AtomicInteger();

        controller.handlePastedLoadResult("secret", failure(LoadFailureReason.UNSUPPORTED_FORMAT), () -> {
            selections.incrementAndGet();
            return Optional.of(KeyStoreContainerType.JKS);
        });

        assertThat(selections).hasValue(0);
        assertThat(executor.submittedTasks()).isEmpty();
    }

    @Test
    @DisplayName("ambiguous pasted result retries the selected container on the executor")
    void ambiguousPastedResultRetriesSelectedContainer() throws Exception {
        RecordingExecutor executor = new RecordingExecutor();
        MainShellController controller = new MainShellController(composition(executor), null);

        controller.handlePastedLoadResult(
                "c2VjcmV0", failure(LoadFailureReason.AMBIGUOUS_CONTAINER), () -> Optional.of(KeyStoreContainerType.BCFKS));

        assertThat(executor.submittedTasks()).hasSize(1);
    }

    @Test
    @DisplayName("cancelled ambiguous selection does not submit a retry")
    void cancelledAmbiguousSelectionDoesNotSubmitARetry() throws Exception {
        RecordingExecutor executor = new RecordingExecutor();
        MainShellController controller = new MainShellController(composition(executor), null);

        controller.handlePastedLoadResult(
                "c2VjcmV0", failure(LoadFailureReason.AMBIGUOUS_CONTAINER), Optional::<KeyStoreContainerType>empty);

        assertThat(executor.submittedTasks()).isEmpty();
    }

    @Test
    @DisplayName("buildInspectView returns a non-null Node once a keystore is loaded")
    void buildInspectViewReturnsNode() throws Exception {
        RecordingExecutor executor = new RecordingExecutor();
        MainShellController controller = new MainShellController(composition(executor), null);
        java.security.cert.X509Certificate cert = CertificateGenerator.selfSigned(
                new javax.security.auth.x500.X500Principal("CN=build"),
                CertificateGenerator.rsaKeyPair(2048),
                "SHA256withRSA", java.time.Duration.ofDays(7));
        controller.handlePastedLoadResult("secret", KeyStoreLoadResult.success(
                KeyStoreContainerType.JKS, "SUN", "17",
                List.of(LoadedEntry.trustedCertificate("alias-1", cert, new java.util.Date()))),
                Optional::<KeyStoreContainerType>empty);

        javafx.scene.Node view = controller.inspectView();
        assertThat(view).isNotNull();
    }

    @Test
    @DisplayName("placeholder is hidden and split is visible after a successful load")
    void placeholderHiddenAfterSuccessfulLoad() throws Exception {
        RecordingExecutor executor = new RecordingExecutor();
        AppComposition comp = composition(executor);
        MainShellController controller = new MainShellController(comp, null);

        // Build the view first so listeners are wired (mirrors show() flow).
        Node view = controller.inspectView();
        Label placeholder = findLabel(view, "Open a KeyStore to inspect.");
        javafx.scene.control.SplitPane split = findNode(view, javafx.scene.control.SplitPane.class);
        assertThat(placeholder).isNotNull();
        assertThat(split).isNotNull();
        assertThat(placeholder.isVisible()).isTrue();  // initial state
        assertThat(split.isVisible()).isFalse();        // initial state

        // Now perform a successful load.
        java.security.cert.X509Certificate cert = CertificateGenerator.selfSigned(
                new javax.security.auth.x500.X500Principal("CN=ok"),
                CertificateGenerator.rsaKeyPair(2048),
                "SHA256withRSA", java.time.Duration.ofDays(7));
        comp.inspectController().onLoadResult(KeyStoreLoadResult.success(
                KeyStoreContainerType.JKS, "SUN", "17",
                List.of(LoadedEntry.trustedCertificate("alias-1", cert, new java.util.Date()))));

        assertThat(placeholder.isVisible()).isFalse();
        assertThat(split.isVisible()).isTrue();
    }

    @Test
    @DisplayName("placeholder shows a 'Load failed: ...' message after a failed load")
    void placeholderShowsFailureReasonAfterFailedLoad() throws Exception {
        RecordingExecutor executor = new RecordingExecutor();
        AppComposition comp = composition(executor);
        MainShellController controller = new MainShellController(comp, null);

        Node view = controller.inspectView();
        Label placeholder = findLabel(view, "Open a KeyStore to inspect.");
        assertThat(placeholder).isNotNull();

        // Simulate a load failure result with a user-readable reason.
        KeyStoreLoadResult failure = KeyStoreLoadResult.failure(
                io.github.certtool.domain.load.KeyFailure.of(
                        io.github.certtool.domain.error.LoadFailureReason.WRONG_STORE_PASSWORD,
                        "Reason stub message"));
        comp.inspectController().onLoadResult(failure);

        assertThat(placeholder.getText()).startsWith("Load failed:");
        assertThat(placeholder.getText()).contains("Reason stub message");
    }

    @Test
    @DisplayName("detail tabs render certificate data once inspection completes")
    void detailTabsRenderCertificateDataOnceInspectionCompletes() throws Exception {
        RecordingExecutor executor = new RecordingExecutor();
        AppComposition comp = composition(executor);
        MainShellController controller = new MainShellController(comp, null);

        // Build the view so listeners are wired.
        Node view = controller.inspectView();
        javafx.scene.control.TabPane tabs = findNode(view, javafx.scene.control.TabPane.class);
        assertThat(tabs).isNotNull();

        // Load + inspect.
        java.security.cert.X509Certificate cert = CertificateGenerator.selfSigned(
                new javax.security.auth.x500.X500Principal("CN=detail"),
                CertificateGenerator.rsaKeyPair(2048),
                "SHA256withRSA", java.time.Duration.ofDays(7));
        KeyStoreLoadResult loaded = KeyStoreLoadResult.success(
                KeyStoreContainerType.JKS, "SUN", "17",
                List.of(LoadedEntry.trustedCertificate("alias-1", cert, new java.util.Date())));
        comp.inspectController().onLoadResult(loaded);
        comp.inspectController().applyInspection(buildInspected("alias-1", cert));

        Node certTab = tabs.getTabs().get(1).getContent();
        Node chainTab = tabs.getTabs().get(2).getContent();
        Node extensionsTab = tabs.getTabs().get(3).getContent();
        Node pemTab = tabs.getTabs().get(4).getContent();

        assertThat(allLabels(certTab))
                .as("Certificate tab should show real cert fields, not the 'Select an alias' placeholder")
                .noneMatch(t -> t.contains("Select an alias"))
                .anyMatch(t -> t.startsWith("Subject:"));
        assertThat(allLabels(chainTab))
                .as("Chain tab should show the chain header, not the 'Select an alias' placeholder")
                .noneMatch(t -> t.contains("Select an alias"))
                .anyMatch(t -> t.contains("certificate(s) in this chain"));
        assertThat(allLabels(extensionsTab))
                .as("Extensions tab should show extension rows, not the 'Select an alias' placeholder")
                .noneMatch(t -> t.contains("Select an alias"))
                .anyMatch(t -> t.startsWith("Basic Constraints"));
        assertThat(allLabels(pemTab))
                .as("PEM tab should show PEM body, not the 'Select an alias' placeholder")
                .noneMatch(t -> t.contains("Select an alias"));
        assertThat(allText(pemTab))
                .as("PEM tab should contain BEGIN CERTIFICATE marker")
                .anyMatch(t -> t.contains("BEGIN CERTIFICATE"));
    }

    @Test
    @DisplayName("complianceView returns a non-null Node containing the disclaimer TextArea, findings TableView, and Run/Export Buttons")
    void complianceViewReturnsNode() throws Exception {
        MainShellController controller = new MainShellController(
                composition(new RecordingExecutor()), null);
        Node view = controller.complianceView();
        assertThat(view).isNotNull();
        assertThat(findNode(view, TextArea.class)).isNotNull();
        assertThat(findNode(view, javafx.scene.control.TableView.class)).isNotNull();
        // "Run Assessment" / "Export Report…" are placed on Button controls, not Labels —
        // use a Button lookup rather than findLabel.
        assertThat(findButton(view, "Run Assessment")).isNotNull();
        assertThat(findButton(view, "Export Report…")).isNotNull();
    }

    @Test
    @DisplayName("onKeyStoreChanged clears any prior assessment report on the compliance VM")
    void onKeyStoreChangedClearsReport() throws Exception {
        AppComposition comp = composition(new RecordingExecutor());
        comp.complianceController().onReportProduced(new AssessmentReport(
                io.github.certtool.compliance.loader.DefaultProfiles.loadFips1403(),
                java.time.Instant.parse("2026-07-15T00:00:00Z"),
                java.util.List.of(new io.github.certtool.domain.assessment.AssessmentFinding(
                        "R-1", "x", io.github.certtool.domain.assessment.AssessmentStatus.PASS,
                        io.github.certtool.domain.assessment.Severity.INFO,
                        "s", "e", "r", java.util.List.of()))));
        MainShellController controller = new MainShellController(comp, null);
        controller.onKeyStoreChanged();
        assertThat(comp.complianceVm().getReport()).isNull();
        assertThat(comp.complianceVm().filteredFindings()).isEmpty();
    }

    @Test
    @DisplayName("Run Assessment disables the Run button while the task is in flight and re-enables it on completion")
    void runAssessmentDisablesButtonWhileInFlight() throws Exception {
        RecordingExecutor executor = new RecordingExecutor();
        AppComposition comp = composition(executor);

        // Set up the state runAssessment() needs to clear its preconditions.
        java.security.cert.X509Certificate cert = CertificateGenerator.selfSigned(
                new javax.security.auth.x500.X500Principal("CN=reentry-guard"),
                CertificateGenerator.rsaKeyPair(2048),
                "SHA256withRSA", java.time.Duration.ofDays(7));
        KeyStoreLoadResult loaded = KeyStoreLoadResult.success(
                KeyStoreContainerType.JKS, "SUN", "17",
                List.of(LoadedEntry.trustedCertificate("alias-x", cert, new java.util.Date())));
        comp.inspectController().onLoadResult(loaded);
        comp.inspectVm().setContentEncoding(ContentEncoding.BINARY);
        comp.inspectController().applyInspection(buildInspected("alias-x", cert));

        MainShellController controller = new MainShellController(comp, null);
        Node view = controller.complianceView();
        Button runButton = findButton(view, "Run Assessment");
        assertThat(runButton).isNotNull();
        assertThat(runButton.isDisabled())
                .as("Run button should start enabled")
                .isFalse();

        // Trigger runAssessment on the FX thread — the button should immediately disable.
        runOnFxThreadAndWait(controller::runAssessment);

        assertThat(runButton.isDisabled())
                .as("Run Assessment must be disabled while a task is in flight")
                .isTrue();

        // Complete the in-flight task by running it (RecordingExecutor never executes
        // submitted tasks, so we drive them to completion here). Running the task performs
        // the call(), transitions to SUCCEEDED, and queues the registered handlers on the
        // JavaFX Application Thread; we then drain that queue to let them run.
        AssessmentTask submitted = controller.currentAssessTask();
        assertThat(submitted)
                .as("runAssessment() must record the in-flight AssessmentTask")
                .isNotNull();
        submitted.run();
        runOnFxThreadAndWait(() -> { });

        assertThat(runButton.isDisabled())
                .as("Run Assessment must be re-enabled after the task completes")
                .isFalse();
    }

    private static InspectedKeyStore buildInspected(String alias, java.security.cert.X509Certificate cert) {
        ValidityWindow validity = new ValidityWindow(Instant.now(), Instant.now().plusSeconds(60));
        CertificateAnalysis analysis = new CertificateAnalysis(
                "CN=detail", "CN=detail", BigInteger.ONE, "01", "1",
                3, validity, ValidityState.VALID, "SHA256withRSA", "1.2.840.113549.1.1.11",
                new PublicKeyInfo(KeyAlgorithm.RSA, 2048, null, null, null),
                new ExtensionAnalysis(
                        BasicConstraintsInfo.absent(), KeyUsageBits.empty(),
                        List.of(), List.of(), List.of(),
                        null, null,
                        List.of(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of()),
                new FingerprintBundle(
                        "e3" + "0".repeat(60), "e2" + "0".repeat(36),
                        "E3" + ":0".repeat(31), "E2" + ":0".repeat(19)),
                new SelfSignedStatus(true, true),
                "-----BEGIN CERTIFICATE-----\nstub\n-----END CERTIFICATE-----\n");
        InspectedEntry entry = new InspectedEntry(
                alias, EntryType.TRUSTED_CERTIFICATE,
                new Date(), true, "RSA", 2048,
                List.of(new InspectedCertificate(0, analysis)),
                List.of());
        return new InspectedKeyStore(
                KeyStoreSummary.from(
                        KeyStoreLoadResult.success(KeyStoreContainerType.JKS, "SUN", "17",
                                List.of(LoadedEntry.trustedCertificate(alias, cert, new java.util.Date()))),
                        ContentEncoding.BINARY),
                List.of(entry));
    }

    private static List<String> allLabels(Node root) {
        List<String> out = new java.util.ArrayList<>();
        collectText(root, out, /*includeTextAreas*/ false);
        return out;
    }

    private static List<String> allText(Node root) {
        List<String> out = new java.util.ArrayList<>();
        collectText(root, out, /*includeTextAreas*/ true);
        return out;
    }

    private static void collectText(Node node, List<String> out, boolean includeTextAreas) {
        if (node == null) return;
        if (node instanceof Label l && l.getText() != null) {
            out.add(l.getText());
        }
        if (includeTextAreas && node instanceof javafx.scene.control.TextArea ta) {
            String t = ta.getText();
            if (t != null) out.add(t);
        }
        if (node instanceof javafx.scene.control.ScrollPane sp) {
            collectText(sp.getContent(), out, includeTextAreas);
        }
        if (node instanceof javafx.scene.Parent p) {
            for (Node child : p.getChildrenUnmodifiable()) {
                collectText(child, out, includeTextAreas);
            }
        }
    }

    private static Label findLabel(Node root, String text) {
        if (root instanceof Label l && text.equals(l.getText())) return l;
        if (root instanceof Label l && l.getText() != null && l.getText().startsWith("Load failed")) return l;
        if (root instanceof javafx.scene.Parent p) {
            for (Node child : p.getChildrenUnmodifiable()) {
                Label found = findLabel(child, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static Button findButton(Node root, String text) {
        if (root instanceof Button b && text.equals(b.getText())) return b;
        if (root instanceof javafx.scene.Parent p) {
            for (Node child : p.getChildrenUnmodifiable()) {
                Button found = findButton(child, text);
                if (found != null) return found;
            }
        }
        return null;
    }
    @SuppressWarnings("unchecked")
    private static <T extends Node> T findNode(Node root, Class<T> type) {
        if (type.isInstance(root)) return type.cast(root);
        if (root instanceof javafx.scene.control.SplitPane sp) {
            for (Node child : sp.getItems()) {
                T found = (T) findNode(child, type);
                if (found != null) return found;
            }
        }
        if (root instanceof javafx.scene.control.TabPane tp) {
            for (javafx.scene.control.Tab tab : tp.getTabs()) {
                T found = (T) findNode(tab.getContent(), type);
                if (found != null) return found;
            }
        }
        if (root instanceof javafx.scene.Parent p) {
            for (Node child : p.getChildrenUnmodifiable()) {
                T found = (T) findNode(child, type);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test
    @DisplayName("restores the persisted Inspect divider position")
    void restoresInspectDividerPosition() throws Exception {
        MainShellController controller =
                new MainShellController(composition(new RecordingExecutor()), null);
        controller.inspectView();

        controller.restoreInspectDividerPosition(
                Settings.defaults().withLeftDividerPosition(0.4));

        assertThat(controller.inspectDividerPosition()).isEqualTo(0.4);
    }

    @Test
    @DisplayName("Overview distinguishes analyzing from no keystore loaded")
    void overviewDistinguishesAnalyzingFromUnloaded() throws Exception {
        MainShellController controller =
                new MainShellController(composition(new RecordingExecutor()), null);
        KeyStoreLoadResult loaded = KeyStoreLoadResult.success(
                KeyStoreContainerType.JKS, "provider", "1", List.of());

        Node analyzing = controller.buildOverviewContent(null, loaded);
        Node unloaded = controller.buildOverviewContent(null, null);

        assertThat(overviewMessage(analyzing)).isEqualTo("Analyzing entries…");
        assertThat(overviewMessage(unloaded)).isEqualTo("No keystore loaded.");
    }

    private static String overviewMessage(Node overview) {
        return ((Label) ((VBox) overview).getChildren().get(0)).getText();
    }

    @Test
    @DisplayName("a stale analyze completion cannot replace the current inspection")
    void staleAnalyzeCompletionDoesNotReplaceCurrentInspection() throws Exception {
        RecordingExecutor executor = new RecordingExecutor();
        AppComposition composition = composition(executor);
        MainShellController controller = new MainShellController(composition, null);
        KeyStoreLoadResult resultA = KeyStoreLoadResult.success(
                KeyStoreContainerType.JKS, "provider-a", "1", List.of());
        KeyStoreLoadResult resultB = KeyStoreLoadResult.success(
                KeyStoreContainerType.BCFKS, "provider-b", "2", List.of());
        Task<KeyStoreLoadResult> loadA = loadTask(resultA);
        Task<KeyStoreLoadResult> loadB = loadTask(resultB);
        AnalyzeKeyStoreTask analyzeA = new AnalyzeKeyStoreTask(resultA, ContentEncoding.BINARY);
        AnalyzeKeyStoreTask analyzeB = new AnalyzeKeyStoreTask(resultB, ContentEncoding.BINARY);
        analyzeB.run();
        InspectedKeyStore expectedB = analyzeB.get(5, TimeUnit.SECONDS);

        runOnFxThreadAndWait(() -> {
            controller.activateLoadTask(loadA);
            controller.submitAnalyzeTask(analyzeA, loadA, () -> { });
            controller.activateLoadTask(loadB);
            controller.submitAnalyzeTask(analyzeB, loadB, () -> { });

            analyzeB.getOnSucceeded().handle(null);
            analyzeA.getOnSucceeded().handle(null);
        });

        assertThat(composition.inspectVm().getInspected()).isSameAs(expectedB);
        assertThat(controller.currentLoadTask()).isSameAs(loadB);
        assertThat(loadA.isCancelled()).isTrue();
        assertThat(analyzeA.isCancelled()).isTrue();
    }

    private static void runOnFxThreadAndWait(Runnable action) throws Exception {
        java.util.concurrent.CountDownLatch completed = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<Throwable> failure =
                new java.util.concurrent.atomic.AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable thrown) {
                failure.set(thrown);
            } finally {
                completed.countDown();
            }
        });
        assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
        if (failure.get() != null) {
            throw new AssertionError("FX action failed", failure.get());
        }
    }

    private static Task<KeyStoreLoadResult> loadTask(KeyStoreLoadResult result) {
        return new Task<>() {
            @Override
            protected KeyStoreLoadResult call() {
                return result;
            }
        };
    }

    private static KeyStoreLoadResult failure(LoadFailureReason reason) {
        return new KeyStoreLoadResult(false, null, null, null, List.of(), LoadFailure.of(reason, "generic"));
    }

    private static AppComposition composition(RecordingExecutor executor) throws Exception {
        InspectViewModel inspectVm = new InspectViewModel();
        ComplianceViewModel complianceVm = new ComplianceViewModel();
        ConvertViewModel convertVm = new ConvertViewModel();
        RuntimeViewModel runtimeVm = new RuntimeViewModel();
        return new AppComposition.Builder()
                .settingsService(new SettingsService(Path.of("target", "test-settings.json")))
                .themeService(new NoOpThemeService())
                .passwordProviderSource(() -> new FixedPasswordProvider(new char[0], Map.of()))
                .backgroundExecutor(executor)
                .analyzerFactory((r, e) -> new io.github.certtool.app.task.AnalyzeKeyStoreTask(
                        r, e))
                .loader(new KeyStoreLoader())
                .assessmentEngine(new AssessmentEngine())
                .ruleRegistry(DefaultRules.registry())
                .inspectVm(inspectVm)
                .complianceVm(complianceVm)
                .convertVm(convertVm)
                .runtimeVm(runtimeVm)
                .inspectController(new InspectController(inspectVm))
                .complianceController(new ComplianceController(complianceVm))
                .convertController(new ConvertController(convertVm))
                .runtimeController(new RuntimeController(runtimeVm))
                .build();
    }

    private static final class RecordingExecutor extends AbstractExecutorService {
        private final List<Runnable> submittedTasks = new java.util.ArrayList<>();

        @Override
        public void execute(Runnable command) {
            submittedTasks.add(command);
        }

        List<Runnable> submittedTasks() {
            return submittedTasks;
        }

        @Override
        public void shutdown() {}

        @Override
        public List<Runnable> shutdownNow() {
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }

    private static final class NoOpThemeService implements ThemeService {
        @Override
        public ThemeMode currentMode() {
            return ThemeMode.LIGHT;
        }

        @Override
        public ThemeMode chosenMode() {
            return ThemeMode.LIGHT;
        }

        @Override
        public void setMode(ThemeMode mode) {}

        @Override
        public boolean supportsSystemListener() {
            return false;
        }
    }
}
