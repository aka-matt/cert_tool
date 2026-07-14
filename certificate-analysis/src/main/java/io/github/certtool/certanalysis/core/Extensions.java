package io.github.certtool.certanalysis.core;

import io.github.certtool.domain.certificate.BasicConstraintsInfo;
import io.github.certtool.domain.certificate.ExtensionAnalysis;
import io.github.certtool.domain.certificate.KeyUsageBits;
import io.github.certtool.domain.certificate.SubjectAlternativeName;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x509.AuthorityInformationAccess;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.CRLDistPoint;
import org.bouncycastle.asn1.x509.CertificatePolicies;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.PolicyInformation;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.cert.X509CertificateHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses the X.509 extensions on a certificate into an {@link ExtensionAnalysis}.
 *
 * <p>Per spec §5 + §17.3, the parser must extract SANs/IANs, EKU OIDs, KU bits, SKI/AKI,
 * Certificate Policies, CRL DPs, AIA, plus critical/non-critical/unrecognized OIDs.
 *
 * <p>Per spec §5, we prefer reliable BC ASN.1 APIs over fragile string splitting. Per spec §2,
 * we must never log key material — we log only the OID and the failure shape, never the value.
 */
public final class Extensions {

    private static final Logger LOG = LoggerFactory.getLogger(Extensions.class);

    private Extensions() {}

    public static ExtensionAnalysis inspect(X509Certificate cert) {
        Objects.requireNonNull(cert, "cert");
        X509CertificateHolder holder;
        try {
            holder = new X509CertificateHolder(cert.getEncoded());
        } catch (Exception e) {
            LOG.warn("Could not decode certificate for extension inspection: {}", e.getClass().getSimpleName());
            return empty();
        }
        return inspect(holder);
    }

    private static ExtensionAnalysis inspect(X509CertificateHolder holder) {
        BasicConstraintsInfo basicConstraints = readBasicConstraints(holder);
        KeyUsageBits keyUsage = readKeyUsage(holder);
        List<String> eku = readExtendedKeyUsage(holder);
        List<SubjectAlternativeName> sans = readGeneralNames(holder, Extension.subjectAlternativeName);
        List<SubjectAlternativeName> ians = readGeneralNames(holder, Extension.issuerAlternativeName);
        String ski = readSubjectKeyIdentifier(holder);
        String aki = readAuthorityKeyIdentifier(holder);
        List<String> policies = readCertificatePolicies(holder);
        List<String> crlUris = readCrlDistributionPoints(holder);
        AuthorityInfoAccessPair aia = readAia(holder);

        List<String> critical = new ArrayList<>();
        List<String> nonCritical = new ArrayList<>();
        List<String> unrecognizedCritical = new ArrayList<>();

        java.util.Set<String> knownOids = knownExtensionOids();
        org.bouncycastle.asn1.x509.Extensions exts = holder.getExtensions();
        if (exts != null) {
            java.util.Enumeration<?> oidEnum = exts.oids();
            while (oidEnum.hasMoreElements()) {
                ASN1ObjectIdentifier oid = (ASN1ObjectIdentifier) oidEnum.nextElement();
                Extension ext = exts.getExtension(oid);
                String id = oid.getId();
                if (ext.isCritical()) {
                    critical.add(id);
                    if (!knownOids.contains(id)) {
                        unrecognizedCritical.add(id);
                    }
                } else {
                    nonCritical.add(id);
                }
            }
        }
        return new ExtensionAnalysis(
                basicConstraints, keyUsage, eku, sans, ians,
                ski, aki, policies, crlUris,
                aia.ocsp, aia.caIssuer,
                critical, nonCritical, unrecognizedCritical);
    }

    private static ExtensionAnalysis empty() {
        return new ExtensionAnalysis(
                BasicConstraintsInfo.absent(),
                KeyUsageBits.empty(),
                List.of(), List.of(), List.of(),
                null, null, List.of(), List.of(),
                List.of(), List.of(),
                List.of(), List.of(), List.of());
    }

    private static BasicConstraintsInfo readBasicConstraints(X509CertificateHolder holder) {
        Extension ext = holder.getExtension(Extension.basicConstraints);
        if (ext == null) {
            return BasicConstraintsInfo.absent();
        }
        try {
            BasicConstraints bc = BasicConstraints.getInstance(ext.getParsedValue());
            java.math.BigInteger pathLen = bc.getPathLenConstraint();
            return new BasicConstraintsInfo(bc.isCA(), pathLen == null ? null : pathLen.intValue());
        } catch (Exception e) {
            LOG.warn("Failed to parse BasicConstraints: {}", e.getClass().getSimpleName());
            return BasicConstraintsInfo.absent();
        }
    }

    private static KeyUsageBits readKeyUsage(X509CertificateHolder holder) {
        Extension ext = holder.getExtension(Extension.keyUsage);
        if (ext == null) {
            return KeyUsageBits.empty();
        }
        try {
            KeyUsage ku = KeyUsage.getInstance(ext.getParsedValue());
            return new KeyUsageBits(
                    hasBit(ku, 0), hasBit(ku, 1), hasBit(ku, 2), hasBit(ku, 3),
                    hasBit(ku, 4), hasBit(ku, 5), hasBit(ku, 6), hasBit(ku, 7), hasBit(ku, 8));
        } catch (Exception e) {
            LOG.warn("Failed to parse KeyUsage: {}", e.getClass().getSimpleName());
            return KeyUsageBits.empty();
        }
    }

    private static boolean hasBit(KeyUsage ku, int bit) {
        byte[] bits = ku.getBytes();
        if (bits.length == 0) {
            return false;
        }
        int byteIdx = bit / 8;
        int bitIdx = 7 - (bit % 8);
        if (byteIdx >= bits.length) {
            return false;
        }
        return (bits[byteIdx] & (1 << bitIdx)) != 0;
    }

    private static List<String> readExtendedKeyUsage(X509CertificateHolder holder) {
        Extension ext = holder.getExtension(Extension.extendedKeyUsage);
        if (ext == null) {
            return List.of();
        }
        try {
            ExtendedKeyUsage eku = ExtendedKeyUsage.getInstance(ext.getParsedValue());
            List<String> result = new ArrayList<>();
            for (org.bouncycastle.asn1.x509.KeyPurposeId o : eku.getUsages()) {
                result.add(o.getId());
            }
            return Collections.unmodifiableList(result);
        } catch (Exception e) {
            LOG.warn("Failed to parse ExtendedKeyUsage: {}", e.getClass().getSimpleName());
            return List.of();
        }
    }

    private static List<SubjectAlternativeName> readGeneralNames(
            X509CertificateHolder holder, ASN1ObjectIdentifier extensionOid) {
        Extension ext = holder.getExtension(extensionOid);
        if (ext == null) {
            return List.of();
        }
        try {
            GeneralNames names = GeneralNames.getInstance(ext.getParsedValue());
            List<SubjectAlternativeName> result = new ArrayList<>();
            for (GeneralName g : names.getNames()) {
                result.add(toSan(g));
            }
            return Collections.unmodifiableList(result);
        } catch (Exception e) {
            LOG.warn("Failed to parse GeneralNames for {}: {}",
                    extensionOid.getId(), e.getClass().getSimpleName());
            return List.of();
        }
    }

    private static SubjectAlternativeName toSan(GeneralName g) {
        ASN1Encodable value = g.getName();
        return new SubjectAlternativeName(g.getTagNo(), value.toString());
    }

    private static String readSubjectKeyIdentifier(X509CertificateHolder holder) {
        Extension ext = holder.getExtension(Extension.subjectKeyIdentifier);
        if (ext == null) {
            return null;
        }
        try {
            SubjectKeyIdentifier ski = SubjectKeyIdentifier.getInstance(ext.getParsedValue());
            return ski.getKeyIdentifier() == null ? null : hex(ski.getKeyIdentifier());
        } catch (Exception e) {
            LOG.warn("Failed to parse SubjectKeyIdentifier: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    private static String readAuthorityKeyIdentifier(X509CertificateHolder holder) {
        Extension ext = holder.getExtension(Extension.authorityKeyIdentifier);
        if (ext == null) {
            return null;
        }
        try {
            AuthorityKeyIdentifier aki = AuthorityKeyIdentifier.getInstance(ext.getParsedValue());
            return aki.getKeyIdentifier() == null ? null : hex(aki.getKeyIdentifier());
        } catch (Exception e) {
            LOG.warn("Failed to parse AuthorityKeyIdentifier: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    private static List<String> readCertificatePolicies(X509CertificateHolder holder) {
        Extension ext = holder.getExtension(Extension.certificatePolicies);
        if (ext == null) {
            return List.of();
        }
        try {
            CertificatePolicies cp = CertificatePolicies.getInstance(ext.getParsedValue());
            List<String> result = new ArrayList<>();
            for (PolicyInformation pi : cp.getPolicyInformation()) {
                result.add(pi.getPolicyIdentifier().getId());
            }
            return Collections.unmodifiableList(result);
        } catch (Exception e) {
            LOG.warn("Failed to parse CertificatePolicies: {}", e.getClass().getSimpleName());
            return List.of();
        }
    }

    private static List<String> readCrlDistributionPoints(X509CertificateHolder holder) {
        Extension ext = holder.getExtension(Extension.cRLDistributionPoints);
        if (ext == null) {
            return List.of();
        }
        try {
            CRLDistPoint dp = CRLDistPoint.getInstance(ext.getParsedValue());
            List<String> result = new ArrayList<>();
            for (org.bouncycastle.asn1.x509.DistributionPoint point : dp.getDistributionPoints()) {
                org.bouncycastle.asn1.x509.DistributionPointName dpn = point.getDistributionPoint();
                if (dpn == null || dpn.getType() != org.bouncycastle.asn1.x509.DistributionPointName.FULL_NAME) {
                    continue;
                }
                ASN1Encodable nameEnc = dpn.getName();
                if (!(nameEnc instanceof GeneralNames names)) {
                    continue;
                }
                for (GeneralName g : names.getNames()) {
                    if (g.getTagNo() == GeneralName.uniformResourceIdentifier) {
                        result.add(g.getName().toString());
                    }
                }
            }
            return Collections.unmodifiableList(result);
        } catch (Exception e) {
            LOG.warn("Failed to parse CRLDistributionPoints: {}", e.getClass().getSimpleName());
            return List.of();
        }
    }

    private record AuthorityInfoAccessPair(List<String> ocsp, List<String> caIssuer) {}

    private static AuthorityInfoAccessPair readAia(X509CertificateHolder holder) {
        Extension ext = holder.getExtension(Extension.authorityInfoAccess);
        if (ext == null) {
            return new AuthorityInfoAccessPair(List.of(), List.of());
        }
        try {
            AuthorityInformationAccess aia = AuthorityInformationAccess.getInstance(ext.getParsedValue());
            List<String> ocsp = new ArrayList<>();
            List<String> caIssuer = new ArrayList<>();
            for (org.bouncycastle.asn1.x509.AccessDescription ad : aia.getAccessDescriptions()) {
                String oid = ad.getAccessMethod().getId();
                if (oid.equals(Extension.authorityInfoAccess.getId())) {
                    continue;
                }
                // OCSP: id-ad-ocsp = 1.3.6.1.5.5.7.48.1
                // CA Issuers: id-ad-caIssuers = 1.3.6.1.5.5.7.48.2
                GeneralName location = ad.getAccessLocation();
                if (location.getTagNo() != GeneralName.uniformResourceIdentifier) {
                    continue;
                }
                String uri = location.getName().toString();
                if (oid.equals("1.3.6.1.5.5.7.48.1")) {
                    ocsp.add(uri);
                } else if (oid.equals("1.3.6.1.5.5.7.48.2")) {
                    caIssuer.add(uri);
                }
            }
            return new AuthorityInfoAccessPair(
                    Collections.unmodifiableList(ocsp),
                    Collections.unmodifiableList(caIssuer));
        } catch (Exception e) {
            LOG.warn("Failed to parse AuthorityInformationAccess: {}", e.getClass().getSimpleName());
            return new AuthorityInfoAccessPair(List.of(), List.of());
        }
    }

    private static java.util.Set<String> knownExtensionOids() {
        return java.util.Set.of(
                Extension.basicConstraints.getId(),
                Extension.keyUsage.getId(),
                Extension.extendedKeyUsage.getId(),
                Extension.subjectAlternativeName.getId(),
                Extension.issuerAlternativeName.getId(),
                Extension.subjectKeyIdentifier.getId(),
                Extension.authorityKeyIdentifier.getId(),
                Extension.certificatePolicies.getId(),
                Extension.cRLDistributionPoints.getId(),
                Extension.authorityInfoAccess.getId(),
                Extension.certificateIssuer.getId(),
                Extension.subjectDirectoryAttributes.getId(),
                Extension.nameConstraints.getId(),
                Extension.policyConstraints.getId(),
                Extension.policyMappings.getId(),
                Extension.inhibitAnyPolicy.getId(),
                Extension.freshestCRL.getId(),
                Extension.subjectInfoAccess.getId()
        );
    }

    private static String hex(byte[] bytes) {
        return java.util.HexFormat.of().formatHex(bytes);
    }
}