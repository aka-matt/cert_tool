package io.github.certtool.app.controller;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.certtool.app.AppComposition;
import io.github.certtool.app.settings.SettingsService;
import io.github.certtool.app.theme.ThemeMode;
import io.github.certtool.app.theme.ThemeService;
import io.github.certtool.app.viewmodel.ComplianceViewModel;
import io.github.certtool.app.viewmodel.ConvertViewModel;
import io.github.certtool.app.viewmodel.InspectViewModel;
import io.github.certtool.app.viewmodel.RuntimeViewModel;
import io.github.certtool.compliance.core.AssessmentEngine;
import io.github.certtool.compliance.core.DefaultRules;
import io.github.certtool.keystorecore.load.KeyStoreLoader;
import io.github.certtool.keystorecore.password.FixedPasswordProvider;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

@DisplayName("MainShellController")
class MainShellControllerTest {

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
