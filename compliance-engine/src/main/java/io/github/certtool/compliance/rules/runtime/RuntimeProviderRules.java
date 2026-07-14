package io.github.certtool.compliance.rules.runtime;

import io.github.certtool.compliance.core.Rule;
import io.github.certtool.compliance.rules.RuleCategory;
import io.github.certtool.domain.assessment.AssessmentFinding;
import io.github.certtool.domain.assessment.AssessmentStatus;
import io.github.certtool.domain.assessment.Severity;
import io.github.certtool.domain.context.ProviderInfo;
import io.github.certtool.domain.context.RuleContext;
import io.github.certtool.domain.profile.Profile;
import java.util.List;
import java.util.Locale;

/** Built-in Runtime Provider rules (spec §7.5). */
public final class RuntimeProviderRules {

    private RuntimeProviderRules() {}

    /** Returns all built-in runtime provider rules in a fixed order. */
    public static List<Rule> all() {
        return List.of(new RequiredProviderRule(), new ApprovedOnlyModeRule(), new NonFipsBouncyCastleRule());
    }

    private static AssessmentFinding finding(
            Rule self, Profile profile, AssessmentStatus status, Severity severity,
            String evidence, String remediation) {
        return new AssessmentFinding(
                self.id(),
                self.title(),
                status,
                severity,
                self.title(),
                evidence,
                remediation,
                profile.references());
    }

    private static boolean nameMatches(ProviderInfo p, String required) {
        if (required == null) {
            return false;
        }
        return p.name().equalsIgnoreCase(required);
    }

    /**
     * Flags runtimes that do not advertise the required JCA provider (typically {@code BCFIPS} for
     * FIPS profiles).
     */
    public static final class RequiredProviderRule implements Rule {
        @Override
        public String id() {
            return "RUNTIME.REQUIRED_PROVIDER";
        }

        @Override
        public String title() {
            return "Required runtime provider is installed";
        }

        @Override
        public RuleCategory category() {
            return RuleCategory.RUNTIME_PROVIDER;
        }

        @Override
        public AssessmentFinding evaluate(RuleContext context, Profile profile) {
            if (!profile.requiresRuntimeProvider()) {
                return finding(this, profile, AssessmentStatus.NOT_APPLICABLE, Severity.INFO,
                        "Profile does not require a specific runtime provider.",
                        "No remediation required.");
            }
            String required = profile.requiredRuntimeProvider();
            boolean present = context.runtime().providers().stream().anyMatch(p -> nameMatches(p, required));
            if (present) {
                return finding(this, profile, AssessmentStatus.PASS, Severity.INFO,
                        "Required provider '" + required + "' is installed.",
                        "No remediation required.");
            }
            String installed = context.runtime().providers().stream().map(ProviderInfo::name).toList().toString();
            return finding(this, profile, AssessmentStatus.FAIL, Severity.HIGH,
                    "Required provider '" + required + "' is not installed; installed providers: " + installed,
                    "Install the " + required + " provider before loading FIPS-protected material.");
        }
    }

    /**
     * Flags runtimes where Approved-Only Mode cannot be confirmed. Per spec §6, when the answer is
     * uncertain, the rule must report {@code NOT_ASSESSABLE} — never guess.
     */
    public static final class ApprovedOnlyModeRule implements Rule {
        @Override
        public String id() {
            return "RUNTIME.APPROVED_ONLY_MODE";
        }

        @Override
        public String title() {
            return "Approved-Only Mode is confirmed";
        }

        @Override
        public RuleCategory category() {
            return RuleCategory.RUNTIME_PROVIDER;
        }

        @Override
        public AssessmentFinding evaluate(RuleContext context, Profile profile) {
            if (!profile.requiresApprovedOnly()) {
                return finding(this, profile, AssessmentStatus.NOT_APPLICABLE, Severity.INFO,
                        "Profile does not require Approved-Only Mode.",
                        "No remediation required.");
            }
            if (context.runtime().approvedOnlyConfirmed()) {
                return finding(this, profile, AssessmentStatus.PASS, Severity.INFO,
                        "A provider has confirmed Approved-Only Mode.",
                        "No remediation required.");
            }
            boolean knownUnknown = context.runtime().providers().stream()
                    .anyMatch(p -> p.approvedOnlyKnown() && !p.approvedOnlyConfirmed());
            if (knownUnknown) {
                return finding(this, profile, AssessmentStatus.NOT_ASSESSABLE, Severity.INFO,
                        "A FIPS provider is installed but Approved-Only Mode could not be confirmed.",
                        "Re-run with a BCFIPS build configured for Approved-Only Mode, or accept this finding.");
            }
            return finding(this, profile, AssessmentStatus.NOT_ASSESSABLE, Severity.INFO,
                    "No FIPS provider installed; Approved-Only Mode cannot be confirmed.",
                    "Install and configure BCFIPS for Approved-Only Mode.");
        }
    }

    /** Flags runtimes that have a non-FIPS Bouncy Castle provider in addition to BCFIPS. */
    public static final class NonFipsBouncyCastleRule implements Rule {
        @Override
        public String id() {
            return "RUNTIME.NON_FIPS_BOUNCY_CASTLE";
        }

        @Override
        public String title() {
            return "No non-FIPS Bouncy Castle provider installed alongside BCFIPS";
        }

        @Override
        public RuleCategory category() {
            return RuleCategory.RUNTIME_PROVIDER;
        }

        @Override
        public AssessmentFinding evaluate(RuleContext context, Profile profile) {
            if (!profile.requiresRuntimeProvider()
                    || !"BCFIPS".equalsIgnoreCase(profile.requiredRuntimeProvider())) {
                return finding(this, profile, AssessmentStatus.NOT_APPLICABLE, Severity.INFO,
                        "Profile does not require BCFIPS.",
                        "No remediation required.");
            }
            boolean fipsInstalled = context.runtime().providers().stream()
                    .anyMatch(p -> "BCFIPS".equalsIgnoreCase(p.name()) || p.fipsLabel().toUpperCase(Locale.ROOT).contains("FIPS"));
            boolean nonFipsInstalled = context.runtime().providers().stream()
                    .anyMatch(p -> {
                        String up = p.name().toUpperCase(Locale.ROOT);
                        return up.startsWith("BC") && !p.isFipsProvider();
                    });
            if (!fipsInstalled) {
                return finding(this, profile, AssessmentStatus.NOT_APPLICABLE, Severity.INFO,
                        "BCFIPS not installed; rule is not applicable.",
                        "No remediation required.");
            }
            if (!nonFipsInstalled) {
                return finding(this, profile, AssessmentStatus.PASS, Severity.INFO,
                        "Only BCFIPS is installed.",
                        "No remediation required.");
            }
            return finding(this, profile, AssessmentStatus.WARNING, Severity.MEDIUM,
                    "A non-FIPS Bouncy Castle provider is installed alongside BCFIPS.",
                    "Remove the non-FIPS provider to avoid unintended use of non-approved algorithms.");
        }
    }
}