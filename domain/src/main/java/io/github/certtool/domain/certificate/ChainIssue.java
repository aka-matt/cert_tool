package io.github.certtool.domain.certificate;

import java.util.Objects;

/**
 * A single issue found in a certificate chain.
 *
 * <p>{@code index} is the 0-based position in the chain (0 = end-entity, last = root). The
 * {@code detail} string carries the issuer/subject string for ISSUER_MISMATCH or other
 * non-sensitive context. It MUST NOT contain private keys, passwords, or full PEM blobs.
 */
public record ChainIssue(ChainIssueCode code, int index, String detail) {

    public ChainIssue {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(detail, "detail");
    }
}