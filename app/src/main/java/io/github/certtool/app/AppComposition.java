package io.github.certtool.app;

import io.github.certtool.app.controller.ComplianceController;
import io.github.certtool.app.controller.ConvertController;
import io.github.certtool.app.controller.InspectController;
import io.github.certtool.app.controller.RuntimeController;
import io.github.certtool.app.settings.SettingsService;
import io.github.certtool.app.task.AssessmentTask;
import io.github.certtool.app.task.AnalyzeKeyStoreTask;
import io.github.certtool.app.task.AutoDetectKeyStoreLoadTask;
import io.github.certtool.app.task.ConvertTask;
import io.github.certtool.app.task.ExportReportTask;
import io.github.certtool.app.task.LoadKeyStoreTask;
import io.github.certtool.app.task.PasteBase64LoadTask;
import io.github.certtool.app.task.SelectedBase64LoadTask;
import io.github.certtool.app.theme.AtlantaFxThemeService;
import io.github.certtool.app.theme.ThemeService;
import io.github.certtool.app.viewmodel.ComplianceViewModel;
import io.github.certtool.app.viewmodel.ConvertViewModel;
import io.github.certtool.app.viewmodel.InspectViewModel;
import io.github.certtool.app.viewmodel.RuntimeViewModel;
import io.github.certtool.compliance.core.AssessmentEngine;
import io.github.certtool.compliance.core.DefaultRules;
import io.github.certtool.compliance.core.RuleRegistry;
import io.github.certtool.domain.context.RuleContext;
import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.domain.load.KeyStoreLoadResult;
import io.github.certtool.domain.profile.Profile;
import io.github.certtool.keystorecore.load.KeyStoreLoader;
import io.github.certtool.keystorecore.password.PasswordProvider;
import io.github.certtool.reporting.ReportEnvelope;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Composition root — wires every collaborator into a single object the main shell consumes.
 *
 * <p>The {@link PasswordProvider} is injected lazily because the JavaFX provider needs a window
 * owner; the main shell builds it and calls {@link #setPasswordProvider(PasswordProvider)} once
 * the primary stage is showing.
 */
public final class AppComposition {

    private final SettingsService settingsService;
    private final ThemeService themeService;
    private final Supplier<PasswordProvider> passwordProviderSource;
    private final ExecutorService backgroundExecutor;
    private final KeyStoreLoader loader;
    private final java.util.function.BiFunction<KeyStoreLoadResult, ContentEncoding, AnalyzeKeyStoreTask> analyzerFactory;
    private final AssessmentEngine assessmentEngine;

    private final InspectViewModel inspectVm;
    private final ComplianceViewModel complianceVm;
    private final ConvertViewModel convertVm;
    private final RuntimeViewModel runtimeVm;

    private final InspectController inspectController;
    private final ComplianceController complianceController;
    private final ConvertController convertController;
    private final RuntimeController runtimeController;

    private final RuleRegistry ruleRegistry;
    private volatile PasswordProvider activePasswordProvider;

    private AppComposition(Builder b) {
        this.settingsService = Objects.requireNonNull(b.settingsService, "settingsService");
        this.themeService = Objects.requireNonNull(b.themeService, "themeService");
        this.passwordProviderSource = Objects.requireNonNull(b.passwordProviderSource, "passwordProviderSource");
        this.backgroundExecutor = Objects.requireNonNull(b.backgroundExecutor, "backgroundExecutor");
        this.loader = Objects.requireNonNull(b.loader, "loader");
        this.analyzerFactory = Objects.requireNonNull(b.analyzerFactory, "analyzerFactory");
        this.assessmentEngine = Objects.requireNonNull(b.assessmentEngine, "assessmentEngine");
        this.ruleRegistry = Objects.requireNonNull(b.ruleRegistry, "ruleRegistry");
        this.inspectVm = Objects.requireNonNull(b.inspectVm, "inspectVm");
        this.complianceVm = Objects.requireNonNull(b.complianceVm, "complianceVm");
        this.convertVm = Objects.requireNonNull(b.convertVm, "convertVm");
        this.runtimeVm = Objects.requireNonNull(b.runtimeVm, "runtimeVm");
        this.inspectController = Objects.requireNonNull(b.inspectController, "inspectController");
        this.complianceController = Objects.requireNonNull(b.complianceController, "complianceController");
        this.convertController = Objects.requireNonNull(b.convertController, "convertController");
        this.runtimeController = Objects.requireNonNull(b.runtimeController, "runtimeController");
        this.activePasswordProvider = passwordProviderSource.get();
    }

    /**
     * Returns the default composition for the application: real services, real providers, real
     * keystore-core / assessment-engine wiring. The password provider is the no-op fixed one until
     * the main shell replaces it with the JavaFX dialog provider.
     */
    public static AppComposition defaultComposition() {
        SettingsService settings = SettingsService.defaults();
        ThemeService theme = new AtlantaFxThemeService(settings, AtlantaFxThemeService.atlantaFxApplier());

        ExecutorService exec = Executors.newFixedThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
                r -> {
                    Thread t = new Thread(r, "cert-tool-background");
                    t.setDaemon(true);
                    return t;
                });

        InspectViewModel inspectVm = new InspectViewModel();
        ComplianceViewModel complianceVm;
        try {
            complianceVm = new ComplianceViewModel();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load built-in compliance profiles", e);
        }
        ConvertViewModel convertVm = new ConvertViewModel();
        RuntimeViewModel runtimeVm = new RuntimeViewModel();

        return new Builder()
                .settingsService(settings)
                .themeService(theme)
                .passwordProviderSource(() -> new io.github.certtool.keystorecore.password.FixedPasswordProvider(
                        new char[0], java.util.Map.of()))
                .backgroundExecutor(exec)
                .loader(new KeyStoreLoader())
                .analyzerFactory(AnalyzeKeyStoreTask::new)
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

    /** Replaces the password provider after the FX stage is showing. */
    public void setPasswordProvider(PasswordProvider provider) {
        this.activePasswordProvider = Objects.requireNonNull(provider, "provider");
    }

    /** Builds a {@link LoadKeyStoreTask} on demand using the active password provider. */
    public LoadKeyStoreTask loadTask(byte[] bytes, KeyStoreContainerType container) {
        return new LoadKeyStoreTask(loader, bytes, container, activePasswordProvider);
    }

    /** Builds an auto-detecting keystore load task using the active password provider. */
    public AutoDetectKeyStoreLoadTask autoDetectLoadTask(byte[] bytes) {
        return new AutoDetectKeyStoreLoadTask(loader, bytes, activePasswordProvider);
    }

    /** Builds an {@link AnalyzeKeyStoreTask} on demand via the configured factory. */
    public AnalyzeKeyStoreTask analyzeTask(KeyStoreLoadResult result, ContentEncoding encoding) {
        return analyzerFactory.apply(result, encoding);
    }

    /** Builds a Base64 paste loading task on demand using the active password provider. */
    public PasteBase64LoadTask pasteBase64LoadTask(String input) {
        return new PasteBase64LoadTask(loader, input, activePasswordProvider);
    }

    /** Builds a selected-format Base64 loading task using the active password provider. */
    public SelectedBase64LoadTask selectedBase64LoadTask(String input, KeyStoreContainerType container) {
        return new SelectedBase64LoadTask(loader, input, container, activePasswordProvider);
    }

    /** Builds an {@link AssessmentTask}. */
    public AssessmentTask assessTask(Profile profile, RuleContext ctx) {
        return new AssessmentTask(assessmentEngine, ctx, profile, ruleRegistry);
    }

    /** Builds a {@link ConvertTask}. */
    public ConvertTask convertTask(io.github.certtool.conversion.domain.plan.ConversionPlan plan, Profile profile) {
        return new ConvertTask(plan, profile, activePasswordProvider);
    }

    /** Builds an {@link ExportReportTask}. */
    public ExportReportTask exportTask(ReportEnvelope env, Path target, ExportReportTask.Format fmt) {
        return new ExportReportTask(env, target, fmt);
    }

    public SettingsService settingsService() { return settingsService; }
    public ThemeService themeService() { return themeService; }
    public ExecutorService backgroundExecutor() { return backgroundExecutor; }
    public AssessmentEngine assessmentEngine() { return assessmentEngine; }
    public InspectViewModel inspectVm() { return inspectVm; }
    public ComplianceViewModel complianceVm() { return complianceVm; }
    public ConvertViewModel convertVm() { return convertVm; }
    public RuntimeViewModel runtimeVm() { return runtimeVm; }
    public InspectController inspectController() { return inspectController; }
    public ComplianceController complianceController() { return complianceController; }
    public ConvertController convertController() { return convertController; }
    public RuntimeController runtimeController() { return runtimeController; }

    /** Builder. */
    public static final class Builder {
        private SettingsService settingsService;
        private ThemeService themeService;
        private Supplier<PasswordProvider> passwordProviderSource;
        private ExecutorService backgroundExecutor;
        private KeyStoreLoader loader;
        private java.util.function.BiFunction<KeyStoreLoadResult, ContentEncoding, AnalyzeKeyStoreTask> analyzerFactory;
        private AssessmentEngine assessmentEngine;
        private RuleRegistry ruleRegistry;
        private InspectViewModel inspectVm;
        private ComplianceViewModel complianceVm;
        private ConvertViewModel convertVm;
        private RuntimeViewModel runtimeVm;
        private InspectController inspectController;
        private ComplianceController complianceController;
        private ConvertController convertController;
        private RuntimeController runtimeController;

        public Builder settingsService(SettingsService v) { this.settingsService = v; return this; }
        public Builder themeService(ThemeService v) { this.themeService = v; return this; }
        public Builder passwordProviderSource(Supplier<PasswordProvider> v) { this.passwordProviderSource = v; return this; }
        public Builder backgroundExecutor(ExecutorService v) { this.backgroundExecutor = v; return this; }
        public Builder loader(KeyStoreLoader v) { this.loader = v; return this; }
        public Builder analyzerFactory(java.util.function.BiFunction<KeyStoreLoadResult, ContentEncoding, AnalyzeKeyStoreTask> v) {
            this.analyzerFactory = v;
            return this;
        }
        public Builder assessmentEngine(AssessmentEngine v) { this.assessmentEngine = v; return this; }
        public Builder ruleRegistry(RuleRegistry v) { this.ruleRegistry = v; return this; }
        public Builder inspectVm(InspectViewModel v) { this.inspectVm = v; return this; }
        public Builder complianceVm(ComplianceViewModel v) { this.complianceVm = v; return this; }
        public Builder convertVm(ConvertViewModel v) { this.convertVm = v; return this; }
        public Builder runtimeVm(RuntimeViewModel v) { this.runtimeVm = v; return this; }
        public Builder inspectController(InspectController v) { this.inspectController = v; return this; }
        public Builder complianceController(ComplianceController v) { this.complianceController = v; return this; }
        public Builder convertController(ConvertController v) { this.convertController = v; return this; }
        public Builder runtimeController(RuntimeController v) { this.runtimeController = v; return this; }

        public AppComposition build() { return new AppComposition(this); }
    }
}
