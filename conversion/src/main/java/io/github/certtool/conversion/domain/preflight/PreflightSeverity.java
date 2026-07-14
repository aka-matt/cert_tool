package io.github.certtool.conversion.domain.preflight;

/**
 * Severity of a {@link PreflightFinding}.
 *
 * <ul>
 *   <li>{@link #BLOCK} — the engine refuses to perform the conversion. The wizard must show this
 *       prominently and require the user to revise the plan.</li>
 *   <li>{@link #WARN} — the engine proceeds but surfaces the issue in the conversion report.</li>
 *   <li>{@link #INFO} — informational; no action required.</li>
 * </ul>
 */
public enum PreflightSeverity {
    BLOCK,
    WARN,
    INFO
}