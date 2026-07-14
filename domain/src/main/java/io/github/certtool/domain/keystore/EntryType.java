package io.github.certtool.domain.keystore;

/**
 * The four user-visible entry kinds recognised in a keystore (per spec §5).
 *
 * <p>{@code UNKNOWN} covers entries that exist but cannot be decoded with the available
 * credentials or providers.
 */
public enum EntryType {
    TRUSTED_CERTIFICATE,
    PRIVATE_KEY,
    SECRET_KEY,
    UNKNOWN
}