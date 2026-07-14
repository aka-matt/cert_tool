package io.github.certtool.reporting;

/**
 * Constants shared by every reporter (spec §9 + §16 schema docs).
 *
 * <p>{@link #REPORT_SCHEMA_VERSION} is embedded in every rendered output so consumers can branch
 * on it. {@link #TOOL_NAME} + {@link #TOOL_VERSION} identify the producer for traceability.
 */
public final class ReportSchema {

    /** Bumped when the JSON shape changes in a way consumers must adapt to. */
    public static final String REPORT_SCHEMA_VERSION = "1.0.0";

    /** Tool product name. */
    public static final String TOOL_NAME = "Cert Tool";

    /** Tool version — kept in lock-step with the Maven {@code project.version}. */
    public static final String TOOL_VERSION = "0.1.0";

    private ReportSchema() {}
}