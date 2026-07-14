package io.github.certtool.conversion.domain.plan;

/**
 * What the engine does when the target file already exists.
 *
 * <ul>
 *   <li>{@link #FAIL_IF_EXISTS} — refuse to write (no exception thrown by the engine — the user
 *       gets a typed preflight BLOCK).</li>
 *   <li>{@link #OVERWRITE} — atomic-replace the existing file (write temp, verify, atomic move).</li>
 * </ul>
 */
public enum OverwritePolicy {
    FAIL_IF_EXISTS,
    OVERWRITE
}