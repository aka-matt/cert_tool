package io.github.certtool.domain.assessment;

/**
 * The mandatory non-certification disclaimer that must accompany every FIPS compatibility
 * assessment report (spec §6, lines 231-279).
 *
 * <p>The tool performs <em>static compatibility assessment</em>. FIPS 140-2/140-3 validation
 * applies to specific cryptographic modules, versions, operating modes, and operational
 * environments. A report produced by this tool is NOT a formal certification conclusion by NIST,
 * CMVP, an accredited lab, or an auditor.
 *
 * <p>Function names must use "FIPS Compatibility Assessment" or "FIPS Readiness Assessment"
 * (Chinese: FIPS 兼容性评估). The tool must never claim "FIPS Certification", "Official FIPS
 * Validation", "NIST Certified", or "正式认证结论".
 */
public final class FipsDisclaimer {

    /** The canonical disclaimer text. */
    public static final String TEXT =
            "This tool performs static compatibility assessment only. FIPS 140-2 and FIPS 140-3 "
                    + "validation applies to specific cryptographic modules, versions, operating "
                    + "modes, and operational environments as evaluated by NIST, CMVP, and "
                    + "accredited laboratories. This report is NOT a formal certification "
                    + "conclusion by NIST, CMVP, an accredited lab, or an auditor, and shall not "
                    + "be represented as such.";

    private FipsDisclaimer() {}

    /** Returns the canonical disclaimer text. */
    public static String text() {
        return TEXT;
    }
}