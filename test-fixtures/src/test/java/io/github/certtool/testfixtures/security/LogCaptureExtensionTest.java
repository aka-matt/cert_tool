package io.github.certtool.testfixtures.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import io.github.certtool.testfixtures.security.LogCaptureExtension.LogCapture;

/**
 * Smoke test for {@link LogCaptureExtension}.
 *
 * <p>Verifies that the extension attaches a {@link ch.qos.logback.core.read.ListAppender} to the
 * root logger and detaches it after the test, and that captured events can be inspected.
 */
@DisplayName("LogCaptureExtension")
@ExtendWith(LogCaptureExtension.class)
class LogCaptureExtensionTest {

    @Test
    @DisplayName("captures log events emitted during the test")
    void capturesEvents(@LogCapture LogCaptureExtension.Capture capture) {
        Logger log = (Logger) LoggerFactory.getLogger("io.github.certtool.smoketest");
        log.info("hello");
        log.warn("careful");

        List<ILoggingEvent> events = capture.events();
        assertThat(events).hasSizeGreaterThanOrEqualTo(2);
        assertThat(events.get(0).getFormattedMessage()).isEqualTo("hello");
    }

    @Test
    @DisplayName("scrubs sensitive shapes from captured events")
    void detectsSensitiveContent(@LogCapture LogCaptureExtension.Capture capture) {
        Logger log = (Logger) LoggerFactory.getLogger("io.github.certtool.smoketest");
        log.info("password=hunter2");

        assertThat(capture.containsSensitiveMaterial()).isTrue();
        assertThatThrownBy(capture::assertNoSensitiveMaterial)
                .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("assertNoSensitiveMaterial passes for clean log lines")
    void passesForCleanLines(@LogCapture LogCaptureExtension.Capture capture) {
        Logger log = (Logger) LoggerFactory.getLogger("io.github.certtool.smoketest");
        log.info("ordinary business message");

        capture.assertNoSensitiveMaterial();
    }
}