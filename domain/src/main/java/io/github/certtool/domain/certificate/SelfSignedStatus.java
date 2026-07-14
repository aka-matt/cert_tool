package io.github.certtool.domain.certificate;

/**
 * Result of checking whether an X.509 certificate is "self-signed" by both structural and
 * cryptographic definitions.
 *
 * <p>Structural: {@code subject == issuer}. Cryptographic: the certificate's signature can be
 * verified with its own embedded public key. Both must be true for a cert to be a genuine
 * self-signed root.
 */
public record SelfSignedStatus(boolean structuralSelfSigned, boolean signatureVerifies) {

    public boolean isFullySelfSigned() {
        return structuralSelfSigned && signatureVerifies;
    }
}