package io.github.certtool.domain.certificate;

import java.util.List;
import java.util.Objects;

/**
 * Descriptive summary of the X.509 extensions on a parsed certificate.
 *
 * <p>Per spec §5, every parsed cert must expose SANs, IANs, EKU OIDs, KU bits, SKI/AKI,
 * Certificate Policies, CRL DPs, AIA, plus the lists of critical, non-critical, and unrecognized
 * critical extension OIDs.
 *
 * <p>Empty / absent extensions are represented by empty immutable lists — never null. This type
 * is descriptive only; the rule engine maps these facts into findings.
 */
public record ExtensionAnalysis(
        BasicConstraintsInfo basicConstraints,
        KeyUsageBits keyUsage,
        List<String> extendedKeyUsageOids,
        List<SubjectAlternativeName> subjectAlternativeNames,
        List<SubjectAlternativeName> issuerAlternativeNames,
        String subjectKeyIdentifier,
        String authorityKeyIdentifier,
        List<String> certificatePolicyOids,
        List<String> crlDistributionPointUris,
        List<String> aiaOcspUris,
        List<String> aiaCaIssuerUris,
        List<String> criticalOids,
        List<String> nonCriticalOids,
        List<String> unrecognizedCriticalOids) {

    public ExtensionAnalysis {
        Objects.requireNonNull(basicConstraints, "basicConstraints");
        Objects.requireNonNull(keyUsage, "keyUsage");
        extendedKeyUsageOids = List.copyOf(extendedKeyUsageOids);
        subjectAlternativeNames = List.copyOf(subjectAlternativeNames);
        issuerAlternativeNames = List.copyOf(issuerAlternativeNames);
        certificatePolicyOids = List.copyOf(certificatePolicyOids);
        crlDistributionPointUris = List.copyOf(crlDistributionPointUris);
        aiaOcspUris = List.copyOf(aiaOcspUris);
        aiaCaIssuerUris = List.copyOf(aiaCaIssuerUris);
        criticalOids = List.copyOf(criticalOids);
        nonCriticalOids = List.copyOf(nonCriticalOids);
        unrecognizedCriticalOids = List.copyOf(unrecognizedCriticalOids);
    }
}