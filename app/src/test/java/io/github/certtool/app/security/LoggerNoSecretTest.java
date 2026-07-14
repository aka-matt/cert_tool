package io.github.certtool.app.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.compliance.loader.DefaultProfiles;
import io.github.certtool.conversion.core.KeystoreConversion;
import io.github.certtool.conversion.domain.plan.AliasConflictPolicy;
import io.github.certtool.conversion.domain.plan.ConversionPlan;
import io.github.certtool.conversion.domain.plan.OverwritePolicy;
import io.github.certtool.conversion.domain.result.ConversionResult;
import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.keystorecore.password.FixedPasswordProvider;
import io.github.certtool.testfixtures.CertificateGenerator;
import io.github.certtool.testfixtures.KeyStoreGenerator;
import io.github.certtool.testfixtures.security.LogCaptureExtension;
import io.github.certtool.testfixtures.security.LogCaptureExtension.Capture;
import io.github.certtool.testfixtures.security.LogCaptureExtension.LogCapture;
import org.junit.jupiter.api.extension.ExtendWith;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import javax.security.auth.x500.X500Principal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

/**
 * Security tests verifying that no log output from a real conversion flow contains the user's
 * store password, the keystore bytes, or the keystore's Base64 form. Per spec §2, a regression here
 * is a critical vulnerability.
 *
 * <p>Drives {@link KeystoreConversion#execute} directly — this is the same code path the
 * {@code ConvertTask} would run on the background executor, so it covers the production logging
 * without requiring an FX toolkit.
 */
@DisplayName("App logger: never logs secrets")
@ExtendWith(LogCaptureExtension.class)
class LoggerNoSecretTest {

    @Test
    @DisplayName("a real conversion does not leak the store password to any logger")
    void conversionDoesNotLeakStorePwd(@TempDir Path tmp, @LogCapture Capture capture)
            throws Exception {
        char[] sensitivePwd = "SUPER-SECRET-PWD-12345".toCharArray();
        KeyPair kp = CertificateGenerator.rsaKeyPair(2048);
        X509Certificate cert = CertificateGenerator.selfSigned(
                new X500Principal("CN=test"), kp, "SHA256withRSA", Duration.ofDays(1));
        byte[] sourceBytes = KeyStoreGenerator.toBytes(
                KeyStoreGenerator.jksBuilder(sensitivePwd).addTrustedCertificate("a", cert).build(),
                sensitivePwd);
        Path source = tmp.resolve("src.jks");
        Path target = tmp.resolve("out.jks");
        Files.write(source, sourceBytes);

        ConversionPlan plan = new ConversionPlan(
                KeyStoreContainerType.JKS, ContentEncoding.BINARY,
                KeyStoreContainerType.JKS, ContentEncoding.BINARY,
                source.toString(), target.toString(),
                sensitivePwd.clone(), sensitivePwd.clone(),
                AliasConflictPolicy.RENAME, OverwritePolicy.OVERWRITE,
                List.of("a"), List.of());

        ConversionResult result = KeystoreConversion.execute(
                plan, DefaultProfiles.loadFips1403(),
                new FixedPasswordProvider(sensitivePwd, Map.of()));
        assertThat(result).isNotNull();

        capture.assertNoSensitiveMaterial();
        String logs = capture.events().stream()
                .map(e -> e.getFormattedMessage())
                .reduce("", (a, b) -> a + "\n" + b);
        assertThat(logs)
                .doesNotContain("SUPER-SECRET-PWD-12345")
                .doesNotContainIgnoringCase("password=");
        // Make sure we didn't echo the keystore bytes either.
        assertThat(logs).doesNotContain("MII"); // base64 JKS magic prefix
    }
}
