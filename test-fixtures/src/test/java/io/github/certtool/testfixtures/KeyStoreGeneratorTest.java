package io.github.certtool.testfixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.certtool.domain.keystore.EntryType;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.x500.X500Principal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("KeyStoreGenerator")
class KeyStoreGeneratorTest {

    private static final X500Principal ROOT_DN = new X500Principal("CN=root");
    private static final char[] STORE_PWD = "storepass".toCharArray();

    @Test
    @DisplayName("builds a JKS keystore with a trusted certificate entry")
    void jksTrustedCertificate() throws Exception {
        KeyStore ks = KeyStoreGenerator.jks(STORE_PWD, "trusted", CertificateGenerator.selfSigned(
                new X500Principal("CN=t"), CertificateGenerator.rsaKeyPair(2048),
                "SHA256withRSA", Duration.ofDays(30)));

        assertThat(ks.getType()).isEqualTo(KeyStoreContainerType.JKS.name());
        assertThat(ks.size()).isEqualTo(1);
        assertThat(ks.isKeyEntry("trusted")).isFalse();
        assertThat(ks.isCertificateEntry("trusted")).isTrue();
        assertThat(ks.getCertificate("trusted")).isNotNull();
    }

    @Test
    @DisplayName("builds a JKS keystore with a private-key entry")
    void jksPrivateKey() throws Exception {
        java.security.KeyPair kp = CertificateGenerator.rsaKeyPair(2048);
        X509Certificate cert = CertificateGenerator.selfSigned(
                new X500Principal("CN=p"), kp, "SHA256withRSA", Duration.ofDays(30));

        KeyStore ks = KeyStoreGenerator.jks(STORE_PWD, "leaf", kp.getPrivate(), "leafpass".toCharArray(), cert);

        assertThat(ks.isKeyEntry("leaf")).isTrue();
        assertThat(ks.getKey("leaf", "leafpass".toCharArray())).isInstanceOf(PrivateKey.class);
        assertThat(ks.getCertificateChain("leaf")).hasSize(1);
    }

    @Test
    @DisplayName("builds a BCFKS keystore with a secret-key entry (JKS does not support SecretKey)")
    void bcfksSecretKey() throws Exception {
        Key secret = new SecretKeySpec("0123456789abcdef".getBytes(StandardCharsets.UTF_8), "AES");
        KeyStore ks = KeyStoreGenerator.bcfks(STORE_PWD, "secret", secret, "secretpass".toCharArray());

        assertThat(ks.getType()).isEqualTo(KeyStoreContainerType.BCFKS.name());
        assertThat(ks.isKeyEntry("secret")).isTrue();
        Key loaded = ks.getKey("secret", "secretpass".toCharArray());
        assertThat(loaded.getAlgorithm()).isEqualTo("AES");
    }

    @Test
    @DisplayName("builds a JKS keystore with multiple aliases and distinct entry passwords")
    void jksMultipleAliases() throws Exception {
        java.security.KeyPair kpA = CertificateGenerator.rsaKeyPair(2048);
        java.security.KeyPair kpB = CertificateGenerator.rsaKeyPair(2048);
        X509Certificate certA = CertificateGenerator.selfSigned(
                new X500Principal("CN=a"), kpA, "SHA256withRSA", Duration.ofDays(30));
        X509Certificate certB = CertificateGenerator.selfSigned(
                new X500Principal("CN=b"), kpB, "SHA256withRSA", Duration.ofDays(30));
        X509Certificate trust = CertificateGenerator.selfSigned(
                new X500Principal("CN=trusted"), CertificateGenerator.rsaKeyPair(2048),
                "SHA256withRSA", Duration.ofDays(30));

        KeyStore ks = KeyStoreGenerator.jksBuilder(STORE_PWD)
                .addPrivateKey("a", kpA.getPrivate(), "passA".toCharArray(), List.of(certA))
                .addPrivateKey("b", kpB.getPrivate(), "passB".toCharArray(), List.of(certB))
                .addTrustedCertificate("trusted", trust)
                .build();

        assertThat(ks.size()).isEqualTo(3);
        assertThat(ks.getKey("a", "passA".toCharArray())).isNotNull();
        assertThat(ks.getKey("b", "passB".toCharArray())).isNotNull();
        // Cross-check: a's password must not unlock b. JKS throws on wrong password.
        assertThatThrownBy(() -> ks.getKey("b", "passA".toCharArray()))
                .isInstanceOf(java.security.UnrecoverableKeyException.class);
        assertThat(ks.getCertificate("trusted")).isNotNull();
    }

    @Test
    @DisplayName("round-trips through binary form")
    void roundTripBinary() throws Exception {
        KeyStore ks = KeyStoreGenerator.jks(STORE_PWD, "t", CertificateGenerator.selfSigned(
                new X500Principal("CN=t"), CertificateGenerator.rsaKeyPair(2048),
                "SHA256withRSA", Duration.ofDays(30)));
        byte[] bytes = KeyStoreGenerator.toBytes(ks, STORE_PWD);

        KeyStore reloaded = KeyStore.getInstance(KeyStoreContainerType.JKS.name());
        reloaded.load(new ByteArrayInputStream(bytes), STORE_PWD);

        assertThat(reloaded.size()).isEqualTo(ks.size());
        assertThat(reloaded.getCertificate("t")).isNotNull();
    }

    @Test
    @DisplayName("round-trips through Base64 form")
    void roundTripBase64() throws Exception {
        KeyStore ks = KeyStoreGenerator.jks(STORE_PWD, "t", CertificateGenerator.selfSigned(
                new X500Principal("CN=t"), CertificateGenerator.rsaKeyPair(2048),
                "SHA256withRSA", Duration.ofDays(30)));
        String b64 = KeyStoreGenerator.toBase64(ks, STORE_PWD);

        byte[] bytes = Base64.getMimeDecoder().decode(b64);
        KeyStore reloaded = KeyStore.getInstance(KeyStoreContainerType.JKS.name());
        reloaded.load(new ByteArrayInputStream(bytes), STORE_PWD);

        assertThat(reloaded.size()).isEqualTo(ks.size());
    }

    @Test
    @DisplayName("builds a BCFKS keystore when configured")
    void bcfks() throws Exception {
        KeyStore ks = KeyStoreGenerator.bcfks(STORE_PWD, "t", CertificateGenerator.selfSigned(
                new X500Principal("CN=t"), CertificateGenerator.rsaKeyPair(2048),
                "SHA256withRSA", Duration.ofDays(30)));

        assertThat(ks.getType()).isEqualTo(KeyStoreContainerType.BCFKS.name());
        assertThat(ks.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("detectEntryType recognises the entry kind (private-key on JKS, secret-key on BCFKS)")
    void detectEntryType() throws Exception {
        java.security.KeyPair kp = CertificateGenerator.rsaKeyPair(2048);
        X509Certificate cert = CertificateGenerator.selfSigned(
                new X500Principal("CN=p"), kp, "SHA256withRSA", Duration.ofDays(30));

        // Private key on JKS.
        KeyStore jksKs =
                KeyStoreGenerator.jks(STORE_PWD, "p", kp.getPrivate(), "pp".toCharArray(), cert);
        jksKs.setCertificateEntry("t", cert);
        assertThat(KeyStoreGenerator.detectEntryType(jksKs, "p", "pp".toCharArray()))
                .isEqualTo(EntryType.PRIVATE_KEY);
        assertThat(KeyStoreGenerator.detectEntryType(jksKs, "t", null))
                .isEqualTo(EntryType.TRUSTED_CERTIFICATE);

        // Secret key on BCFKS (JKS does not support SecretKey).
        Key secret = new SecretKeySpec("0123456789abcdef".getBytes(StandardCharsets.UTF_8), "AES");
        KeyStore bcfksKs = KeyStoreGenerator.bcfks(STORE_PWD, "s", secret, "sp".toCharArray());
        assertThat(KeyStoreGenerator.detectEntryType(bcfksKs, "s", "sp".toCharArray()))
                .isEqualTo(EntryType.SECRET_KEY);
    }

    @Test
    @DisplayName("writeToBytes and loadFromBytes work for BCFKS")
    void bcfksWriteLoad() throws Exception {
        KeyStore ks = KeyStoreGenerator.bcfks(STORE_PWD, "t", CertificateGenerator.selfSigned(
                new X500Principal("CN=t"), CertificateGenerator.rsaKeyPair(2048),
                "SHA256withRSA", Duration.ofDays(30)));
        byte[] bytes = KeyStoreGenerator.toBytes(ks, STORE_PWD);

        KeyStore reloaded = KeyStore.getInstance(KeyStoreContainerType.BCFKS.name(), "BC");
        reloaded.load(new ByteArrayInputStream(bytes), STORE_PWD);
        Certificate c = reloaded.getCertificate("t");
        assertThat(c).isNotNull();
    }

    @Test
    @DisplayName("jksBuilder rejects duplicate alias")
    void rejectsDuplicateAlias() {
        KeyStoreGenerator.JksBuilder b = KeyStoreGenerator.jksBuilder(STORE_PWD);
        b.addTrustedCertificate("dup", CertificateGenerator.selfSigned(
                new X500Principal("CN=1"), CertificateGenerator.rsaKeyPair(2048),
                "SHA256withRSA", Duration.ofDays(30)));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                b.addTrustedCertificate("dup", CertificateGenerator.selfSigned(
                        new X500Principal("CN=2"), CertificateGenerator.rsaKeyPair(2048),
                        "SHA256withRSA", Duration.ofDays(30))));
    }
}