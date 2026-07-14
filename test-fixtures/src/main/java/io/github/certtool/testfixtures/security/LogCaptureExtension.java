package io.github.certtool.testfixtures.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.slf4j.LoggerFactory;

/**
 * JUnit 5 extension that captures SLF4J/Logback events emitted during a test and exposes them
 * through a {@link Capture} parameter.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * @Test
 * void noSecretsInLogs(@LogCapture LogCaptureExtension.Capture capture) {
 *     service.doThing(); // emits log lines
 *     capture.assertNoSensitiveMaterial();
 * }
 * }</pre>
 *
 * <p>The extension attaches a {@link ListAppender} at INFO to the root logger for the duration of
 * the test, and detaches it in {@link AfterEachCallback}. Tests run in parallel by default in JUnit
 * 5; for parallel runs, use the per-logger variant or set
 * {@code junit.jupiter.execution.parallel.enabled=false}.
 */
public final class LogCaptureExtension implements ParameterResolver, AfterEachCallback {

    /** Marks a {@link Capture} parameter as one to be supplied by this extension. */
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @java.lang.annotation.Target(java.lang.annotation.ElementType.PARAMETER)
    public @interface LogCapture {}

    /** Captured events for one test method. */
    public static final class Capture {
        private final ListAppender<ILoggingEvent> appender;

        Capture(ListAppender<ILoggingEvent> appender) {
            this.appender = appender;
        }

        public List<ILoggingEvent> events() {
            return List.copyOf(appender.list);
        }

        public boolean containsSensitiveMaterial() {
            return events().stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .anyMatch(SecretScrubber::containsSensitiveMaterial);
        }

        /** Throws {@link AssertionError} if any captured event contains sensitive material. */
        public void assertNoSensitiveMaterial() {
            for (ILoggingEvent e : events()) {
                if (SecretScrubber.containsSensitiveMaterial(e.getFormattedMessage())) {
                    throw new AssertionError(
                            "Sensitive material detected in log event: "
                                    + SecretScrubber.scrub(e.getFormattedMessage()));
                }
            }
        }
    }

    @Override
    public boolean supportsParameter(
            ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        return parameterContext.getParameter().getType() == Capture.class
                && parameterContext.isAnnotated(LogCapture.class);
    }

    @Override
    public Object resolveParameter(
            ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext(root.getLoggerContext());
        appender.start();
        root.addAppender(appender);
        root.setLevel(Level.INFO);
        extensionContext
                .getStore(ExtensionContext.Namespace.create(LogCaptureExtension.class))
                .put("appender", appender);
        return new Capture(appender);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        var store = context.getStore(ExtensionContext.Namespace.create(LogCaptureExtension.class));
        ListAppender<ILoggingEvent> appender =
                store.remove("appender", ListAppender.class);
        if (appender != null) {
            Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
            root.detachAppender(appender);
            appender.stop();
        }
    }
}