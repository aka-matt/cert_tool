package io.github.certtool.certanalysis.core;

import io.github.certtool.domain.certificate.CertificateAnalysis;
import io.github.certtool.domain.certificate.ExtensionAnalysis;
import io.github.certtool.domain.certificate.FingerprintBundle;
import io.github.certtool.domain.certificate.PublicKeyInfo;
import io.github.certtool.domain.certificate.SelfSignedStatus;
import io.github.certtool.domain.certificate.ValidityState;
import io.github.certtool.domain.certificate.ValidityWindow;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Top-level entry point for parsing a single X.509 certificate into a {@link CertificateAnalysis}.
 *
 * <p>This is the composable core of Phase 3. It pulls together: extensions (BC ASN.1),
 * fingerprints (JDK MessageDigest), self-signed status, public-key descriptor, and PEM.
 *
 * <p>Per spec §2 + §5, the analyzer MUST NOT log any sensitive material. Per spec §6 + §17.3,
 * it is descriptive only — no FIPS verdicts.
 */
public final class CertificateAnalyzer {

    private static final Logger LOG = LoggerFactory.getLogger(CertificateAnalyzer.class);

    private CertificateAnalyzer() {}

    public static CertificateAnalysis analyze(X509Certificate cert) {
        Objects.requireNonNull(cert, "cert");
        try {
            String subject = cert.getSubjectX500Principal().getName();
            String issuer = cert.getIssuerX500Principal().getName();
            BigInteger serial = cert.getSerialNumber();
            String serialHex = HexFormat.of().formatHex(serial.toByteArray());
            String serialDec = serial.toString();
            int version = cert.getVersion();
            ValidityWindow validity = new ValidityWindow(
                    cert.getNotBefore().toInstant(),
                    cert.getNotAfter().toInstant());
            ValidityState current = validity.validAt(Instant.now());
            String sigAlg = cert.getSigAlgName();
            String sigAlgOid = resolveSigAlgOid(cert);
            PublicKeyInfo pki = PublicKeyInfo.of(cert.getPublicKey());
            ExtensionAnalysis exts = Extensions.inspect(cert);
            FingerprintBundle fp = Fingerprints.of(cert);
            SelfSignedStatus ss = SelfSignedVerifier.check(cert);
            String pem = toPem(cert);

            return new CertificateAnalysis(
                    subject, issuer, serial, serialHex, serialDec,
                    version, validity, current,
                    sigAlg, sigAlgOid, pki, exts, fp, ss, pem);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to analyze certificate", e);
        }
    }

    private static String resolveSigAlgOid(X509Certificate cert) {
        try {
            byte[] sigAlgParams = cert.getSigAlgParams();
            if (sigAlgParams == null) {
                return new DefaultDigestAlgorithmIdentifierFinder()
                        .find(cert.getSigAlgName()).getAlgorithm().getId();
            }
            return cert.getSigAlgOID();
        } catch (Exception e) {
            LOG.warn("Could not resolve signature algorithm OID: {}", e.getClass().getSimpleName());
            return cert.getSigAlgOID();
        }
    }

    private static String toPem(X509Certificate cert) throws CertificateEncodingException, IOException {
        StringWriter sw = new StringWriter();
        try (JcaPEMWriter writer = new JcaPEMWriter(sw)) {
            writer.writeObject(cert);
        }
        return sw.toString();
    }
}