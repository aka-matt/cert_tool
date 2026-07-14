package io.github.certtool.domain.certificate;

/**
 * Key Usage bits per RFC 5280 §4.2.1.3. Every field maps to a bit position in the 9-bit
 * DER-encoded KeyUsage value.
 *
 * <p>{@code keyCertSign} (bit 5) is what the chain validator checks for whether a CA may sign
 * subordinate certificates.
 */
public record KeyUsageBits(
        boolean digitalSignature,
        boolean nonRepudiation,
        boolean keyEncipherment,
        boolean dataEncipherment,
        boolean keyAgreement,
        boolean keyCertSign,
        boolean cRLSign,
        boolean encipherOnly,
        boolean decipherOnly) {

    public static KeyUsageBits empty() {
        return new KeyUsageBits(false, false, false, false, false, false, false, false, false);
    }
}