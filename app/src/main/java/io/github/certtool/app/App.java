package io.github.certtool.app;

import io.github.certtool.app.controller.MainShellController;
import io.github.certtool.app.password.JavaFxPasswordProvider;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Application entry point. Wires the composition root, builds the JavaFX stage, and hands control
 * to {@link MainShellController}.
 *
 * <p>Tests must not start this class — they use the controllers and tasks directly.
 */
public final class App extends Application {

    private static final Logger LOG = LoggerFactory.getLogger(App.class);

    private AppComposition composition;
    private MainShellController shell;

    @Override
    public void start(Stage primaryStage) {
        try {
            composition = AppComposition.defaultComposition();
            shell = new MainShellController(composition, primaryStage);
            shell.show();

            // Now that the stage is showing, build the dialog-backed password provider.
            composition.setPasswordProvider(JavaFxPasswordProvider.dialogProvider(primaryStage));

            // Persist window bounds when the user closes the window.
            primaryStage.setOnCloseRequest(evt -> shell.persistBounds());
        } catch (Throwable t) {
            LOG.error("Application startup failed", t);
            Platform.exit();
        }
    }

    @Override
    public void stop() {
        if (composition != null) {
            composition.backgroundExecutor().shutdown();
        }
    }
}
