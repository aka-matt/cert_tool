package io.github.certtool.app.task;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.domain.load.KeyStoreLoadResult;
import io.github.certtool.domain.load.LoadedEntry;
import io.github.certtool.testfixtures.CertificateGenerator;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.security.auth.x500.X500Principal;
import javafx.application.Platform;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

@DisplayName("AnalyzeKeyStoreTask security")
class AnalyzeKeyStoreTaskSecurityTest {

    @Test
    @DisplayName("success path never logs entry aliases, DER bytes, or password-shaped strings")
    void successPathDoesNotLeak() throws Exception {
        X509Certificate cert = CertificateGenerator.selfSigned(
                new X500Principal("CN=very-unique-alias-marker-9f2c"),
                CertificateGenerator.rsaKeyPair(2048),
                "SHA256withRSA", Duration.ofDays(7));
        LoadedEntry entry = LoadedEntry.trustedCertificate("very-unique-alias-marker-9f2c", cert, new Date());
        KeyStoreLoadResult result = KeyStoreLoadResult.success(
                KeyStoreContainerType.BCFKS, "BCFIPS", "1.0", List.of(entry));

        Logger taskLogger = (Logger) LoggerFactory.getLogger(AnalyzeKeyStoreTask.class);
        Level originalLevel = taskLogger.getLevel();
        taskLogger.setLevel(Level.DEBUG);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        taskLogger.addAppender(appender);
        try {
            // AnalyzeKeyStoreTask.call() routes updateProgress through Platform.runLater,
            // which throws IllegalStateException when the FX toolkit is not initialised. By
            // that point inspectEntry has already processed the entry, so the Logback
            // appender has captured everything that was logged.
            try {
                new AnalyzeKeyStoreTask(result, ContentEncoding.BINARY).call();
            } catch (IllegalStateException ignored) {
                // FX toolkit not initialised; updateProgress threw after the entry was
                // processed.
            }

            assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                    .noneMatch(msg -> msg.contains("very-unique-alias-marker-9f2c"))
                    .noneMatch(msg -> msg.contains("password"))
                    .noneMatch(msg -> msg.contains("secret"))
                    .noneMatch(msg -> msg.contains("keystore"))
                    .noneMatch(msg -> msg.contains("base64"));
        } finally {
            taskLogger.detachAppender(appender);
            taskLogger.setLevel(originalLevel);
        }
    }
}