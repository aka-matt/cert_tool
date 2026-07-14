package io.github.certtool.domain.inspect;

import io.github.certtool.domain.certificate.CertificateAnalysis;
import java.util.Objects;

/** One analyzed certificate inside an inspected entry. */
public record InspectedCertificate(int chainIndex, CertificateAnalysis analysis) {

    public InspectedCertificate {
        if (chainIndex < 0) {
            throw new IllegalArgumentException("chainIndex must be >= 0");
        }
        Objects.requireNonNull(analysis, "analysis");
    }
}
