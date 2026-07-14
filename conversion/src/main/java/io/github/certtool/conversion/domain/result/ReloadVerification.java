package io.github.certtool.conversion.domain.result;

import java.util.Objects;

/**
 * Result of reloading the written target keystore and comparing it to the source. The engine
 * requires every numeric counter to match for the conversion to be reported as successful (spec
 * §9 step 5).
 */
public record ReloadVerification(
        boolean reloadSucceeded,
        int sourceAliasCount,
        int targetAliasCount,
        int sourceCertificateCount,
        int targetCertificateCount,
        boolean fingerprintsMatch) {

    public ReloadVerification {
        Objects.requireNonNull(this, "");
    }

    public boolean matchesSource() {
        return reloadSucceeded
                && sourceAliasCount == targetAliasCount
                && sourceCertificateCount == targetCertificateCount
                && fingerprintsMatch;
    }
}