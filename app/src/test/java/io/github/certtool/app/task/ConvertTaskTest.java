package io.github.certtool.app.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.certtool.compliance.loader.DefaultProfiles;
import io.github.certtool.conversion.domain.plan.AliasConflictPolicy;
import io.github.certtool.conversion.domain.plan.ConversionPlan;
import io.github.certtool.conversion.domain.plan.OverwritePolicy;
import io.github.certtool.conversion.core.PreflightBlockedException;
import io.github.certtool.conversion.domain.result.ConversionResult;
import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.keystorecore.password.FixedPasswordProvider;
import io.github.certtool.testfixtures.CertificateGenerator;
import io.github.certtool.testfixtures.KeyStoreGenerator;
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
import org.junit.jupiter.api.io.TempDir;

@DisplayName("ConvertTask")
class ConvertTaskTest {

    @Test
    @DisplayName("call() succeeds for a JKS-to-JKS conversion, copying the alias and verifying reload")
    void copiesJksToJks(@TempDir Path tmp) throws Exception {
        char[] storePwd = "store-pwd".toCharArray();
        KeyPair kp = CertificateGenerator.rsaKeyPair(2048);
        X509Certificate cert = CertificateGenerator.selfSigned(
                new X500Principal("CN=test"), kp, "SHA256withRSA", Duration.ofDays(1));
        byte[] sourceBytes = KeyStoreGenerator.toBytes(
                KeyStoreGenerator.jksBuilder(storePwd).addTrustedCertificate("alias-a", cert).build(),
                storePwd);
        Path source = tmp.resolve("src.jks");
        Path target = tmp.resolve("out.jks");
        Files.write(source, sourceBytes);

        ConversionPlan plan = new ConversionPlan(
                KeyStoreContainerType.JKS, ContentEncoding.BINARY,
                KeyStoreContainerType.JKS, ContentEncoding.BINARY,
                source.toString(), target.toString(),
                storePwd.clone(), storePwd.clone(),
                AliasConflictPolicy.RENAME, OverwritePolicy.OVERWRITE,
                List.of("alias-a"), List.of());

        ConvertTask task = new ConvertTask(plan, DefaultProfiles.loadFips1403(),
                new FixedPasswordProvider(storePwd, Map.of()));
        ConversionResult result = task.call();

        assertThat(result).isNotNull();
        assertThat(result.verification().targetAliasCount()).isEqualTo(1);
        assertThat(result.verification().fingerprintsMatch()).isTrue();
        assertThat(Files.exists(target)).isTrue();
        assertThat(Files.exists(source)).isTrue(); // source untouched
    }

    @Test
    @DisplayName("call() bubbles up PreflightBlockedException when the plan triggers a BLOCK finding")
    void preflightBlocks(@TempDir Path tmp) throws Exception {
        char[] storePwd = "store-pwd".toCharArray();
        // valid source so the load succeeds; pre-existing target + FAIL_IF_EXISTS → BLOCK
        KeyPair kp = CertificateGenerator.rsaKeyPair(2048);
        X509Certificate cert = CertificateGenerator.selfSigned(
                new X500Principal("CN=test"), kp, "SHA256withRSA", Duration.ofDays(1));
        byte[] sourceBytes = KeyStoreGenerator.toBytes(
                KeyStoreGenerator.jksBuilder(storePwd).addTrustedCertificate("a", cert).build(),
                storePwd);
        Path source = tmp.resolve("src.jks");
        Path target = tmp.resolve("out.jks");
        Files.write(source, sourceBytes);
        Files.write(target, new byte[]{0}); // any file present triggers FAIL_IF_EXISTS

        ConversionPlan plan = new ConversionPlan(
                KeyStoreContainerType.JKS, ContentEncoding.BINARY,
                KeyStoreContainerType.JKS, ContentEncoding.BINARY,
                source.toString(), target.toString(),
                storePwd.clone(), storePwd.clone(),
                AliasConflictPolicy.RENAME, OverwritePolicy.FAIL_IF_EXISTS,
                List.of("a"), List.of());

        ConvertTask task = new ConvertTask(plan, DefaultProfiles.loadFips1403(),
                new FixedPasswordProvider(storePwd, Map.of()));
        assertThatThrownBy(task::call).isInstanceOf(PreflightBlockedException.class);
    }
}
