package io.github.certtool.conversion.domain.plan;

/**
 * How the engine resolves aliases that already exist on the target keystore when copying entries.
 *
 * <ul>
 *   <li>{@link #SKIP} — leave the existing target entry untouched and drop the incoming entry.</li>
 *   <li>{@link #RENAME} — rename the incoming entry ({@code alias-1}, {@code alias-2}, …) so the
 *       original is preserved on the target.</li>
 *   <li>{@link #OVERWRITE} — replace the existing target entry with the incoming one. This
 *       changes the bytes of the existing alias; it is the user's explicit choice in step 3.</li>
 * </ul>
 */
public enum AliasConflictPolicy {
    SKIP,
    RENAME,
    OVERWRITE
}