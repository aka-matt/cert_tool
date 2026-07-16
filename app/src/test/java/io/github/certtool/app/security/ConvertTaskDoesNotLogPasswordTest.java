package io.github.certtool.app.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.app.controller.ConvertController;
import io.github.certtool.app.controller.ConvertWizardController;
import io.github.certtool.app.task.ConvertPreflightTask;
import io.github.certtool.app.viewmodel.ConvertWizardViewModel;
import io.github.certtool.conversion.domain.plan.AliasConflictPolicy;
import io.github.certtool.conversion.domain.plan.ConversionPlan;
import io.github.certtool.conversion.domain.plan.OverwritePolicy;
import io.github.certtool.domain.context.LoadedKeyStoreInfo;
import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.keystore.EntryType;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Security tests verifying that passwords are never exposed through task messages,
 * error listeners, or exception text during preflight and conversion.
 */
class ConvertTaskDoesNotLogPasswordTest {

    @BeforeAll
    static void initFx() {
        // Headless: same trick used by ConvertWizardControllerTest.
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {}
    }

    /**
     * Verifies ConvertPreflightTask does not include the target store password in its
     * update message. The task only logs "Preflighting..." and
     * "Preflight complete: N blockers, M warnings." so this should pass immediately.
     */
    @Test
    void preflightTaskUpdateMessageDoesNotIncludeTargetPassword() throws Exception {
        char[] pwd = "secret-pw-xyz".toCharArray();
        var plan = new ConversionPlan(
                KeyStoreContainerType.JKS, ContentEncoding.BINARY,
                KeyStoreContainerType.BCFKS, ContentEncoding.BINARY,
                "/tmp/s", "/tmp/t",
                "src".toCharArray(), pwd,
                AliasConflictPolicy.RENAME, OverwritePolicy.FAIL_IF_EXISTS,
                List.of("a"), List.of(new char[0]));
        var task = new ConvertPreflightTask(plan, Map.of("a", EntryType.TRUSTED_CERTIFICATE), null);
        // call() is protected — invoke via reflection
        Method m = ConvertPreflightTask.class.getDeclaredMethod("call");
        m.setAccessible(true);
        m.invoke(task);
        String msg = task.getMessage();
        assertThat(msg).doesNotContain("secret-pw-xyz");
    }

    /**
     * Verifies the wizard's preflight error listener does not receive the password when
     * the preflight task throws a RuntimeException with the password as its message.
     *
     * <p>Pattern mirrors {@link io.github.certtool.app.controller.ConvertWizardControllerTest}:
     * the factory returns a task that throws synchronously inside {@code call()}.
     * The controller catches it at line 244-245 and calls {@code onPreflightFailed} directly.
     * This means the error arrives via the direct call, not via {@code setOnFailed}.
     *
     * <p>The error listener must NOT receive the password text — only the class name
     * and a generic redaction note.
     */
    @Test
    void wizardErrorListenerDoesNotReceivePassword() throws Exception {
        var vm = new ConvertWizardViewModel();
        var ref4StatusMessage = new AtomicReference<String>();

        var wiz = new ConvertWizardController(
                new ConvertController(vm),
                vm,
                java.util.concurrent.Executors.newSingleThreadExecutor(),
                r -> {},            // onConvertSucceeded — not relevant here
                ref4StatusMessage::set);

        var errors = new AtomicReference<String>();
        wiz.setPreflightErrorListener(errors::set);

        // Seed source state via onSourceSelected (the production wiring path)
        new ConvertController(vm).onSourceSelected(
                new LoadedKeyStoreInfo(
                        KeyStoreContainerType.JKS, ContentEncoding.BINARY,
                        Path.of("/tmp/s").toString(), 1024L, true, List.of("a")));
        vm.setTargetPath("/tmp/t");

        wiz.setPreflightTaskFactory((plan, profile) -> {
            // Subclass that throws with the password as message — mirrors the existing
            // test pattern in ConvertWizardControllerTest line 203-212.
            return new ConvertPreflightTask(plan,
                    Map.of("a", EntryType.TRUSTED_CERTIFICATE), profile) {
                @Override
                public io.github.certtool.conversion.domain.preflight.PreflightReport call()
                        throws Exception {
                    throw new RuntimeException("secret-pw-xyz");
                }
            };
        });

        // Set runningPreflight=true and call handleEnteredPreflight on FX thread
        runOnFxThreadAndWait(() -> {
            vm.setRunningPreflight(true); // simulate what submit() does
            wiz.handleEnteredPreflight(
                    Map.of("a", EntryType.TRUSTED_CERTIFICATE), null);
        });

        // Allow the executor + FX event queue to drain so setOnFailed fires.
        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {}

        // Per CLAUDE.md §2 rule 10: error messages must not leak key material.
        // The listener must NOT receive the password text.
        assertThat(errors.get()).doesNotContain("secret-pw-xyz");
        // The listener SHOULD receive the exception class name (useful diagnostic).
        assertThat(errors.get()).contains("RuntimeException");
    }

    // ─── Helpers (copied from ConvertWizardControllerTest) ────────────────────────

    private static void runOnFxThreadAndWait(Runnable action) throws Exception {
        java.util.concurrent.CountDownLatch completed = new java.util.concurrent.CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable thrown) {
                failure.set(thrown);
            } finally {
                completed.countDown();
            }
        });
        if (!completed.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
            throw new AssertionError("FX thread did not respond within 5 seconds");
        }
        if (failure.get() != null) {
            throw new AssertionError("FX thread threw", failure.get());
        }
    }
}
