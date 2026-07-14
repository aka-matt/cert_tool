package io.github.certtool.compliance.rules.container;

import io.github.certtool.compliance.core.Rule;
import io.github.certtool.compliance.rules.RuleCategory;
import io.github.certtool.domain.assessment.AssessmentFinding;
import io.github.certtool.domain.assessment.AssessmentStatus;
import io.github.certtool.domain.assessment.Severity;
import io.github.certtool.domain.context.RuleContext;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.domain.profile.JksPrivateKeyPolicy;
import io.github.certtool.domain.profile.Profile;
import java.util.List;

/** Built-in Container rules (spec §7.1). */
public final class ContainerRules {

    private ContainerRules() {}

    /** Returns all built-in container rules in a fixed order. */
    public static List<Rule> all() {
        return List.of(new JksPrivateKeyRule(), new JksIntegrityRule());
    }

    /**
     * Flags JKS keystores that contain private-key entries. JKS uses a weak proprietary integrity
     * check and does not encrypt private-key material with strong cryptography.
     */
    public static final class JksPrivateKeyRule implements Rule {

        @Override
        public String id() {
            return "CONTAINER.JKS_PRIVATE_KEY";
        }

        @Override
        public String title() {
            return "JKS keystore contains private-key entries";
        }

        @Override
        public RuleCategory category() {
            return RuleCategory.CONTAINER;
        }

        @Override
        public AssessmentFinding evaluate(RuleContext context, Profile profile) {
            boolean isJks = context.loadedKeyStore().containerType() == KeyStoreContainerType.JKS;
            if (!isJks) {
                return finding(profile, AssessmentStatus.NOT_APPLICABLE, "Container is not JKS.");
            }
            if (!context.hasPrivateKeys()) {
                return finding(profile, AssessmentStatus.PASS, "JKS contains no private-key entries.");
            }
            String evidence = "Private-key entries: "
                    + context.entries().stream()
                            .filter(e -> e.isPrivateKey())
                            .map(e -> e.alias())
                            .toList();
            return switch (profile.jksPrivateKeyPolicy()) {
                case FAIL -> finding(profile, AssessmentStatus.FAIL, evidence,
                        "Migrate private-key entries to BCFKS or PKCS#12; JKS uses a weak integrity check.");
                case WARNING -> finding(profile, AssessmentStatus.WARNING, evidence,
                        "Consider migrating private-key entries to BCFKS or PKCS#12.");
                case NOT_ASSESSABLE -> finding(profile, AssessmentStatus.NOT_ASSESSABLE, evidence,
                        "Tool cannot determine whether private keys are securely stored in JKS.");
            };
        }

        private AssessmentFinding finding(Profile p, AssessmentStatus status, String evidence) {
            return finding(p, status, evidence, "No remediation required.");
        }

        private AssessmentFinding finding(
                Profile p, AssessmentStatus status, String evidence, String remediation) {
            Severity severity = switch (status) {
                case FAIL -> Severity.HIGH;
                case WARNING -> Severity.MEDIUM;
                case PASS -> Severity.INFO;
                default -> Severity.INFO;
            };
            return new AssessmentFinding(
                    id(),
                    title(),
                    status,
                    severity,
                    "JKS private-key handling check",
                    evidence,
                    remediation,
                    p.references());
        }
    }

    /**
     * Flags JKS keystores whose integrity check did not pass. JKS's per-byte XOR checksum is
     * extremely weak; a failed check indicates likely tampering or corruption.
     */
    public static final class JksIntegrityRule implements Rule {

        @Override
        public String id() {
            return "CONTAINER.JKS_INTEGRITY";
        }

        @Override
        public String title() {
            return "JKS integrity check passed";
        }

        @Override
        public RuleCategory category() {
            return RuleCategory.CONTAINER;
        }

        @Override
        public AssessmentFinding evaluate(RuleContext context, Profile profile) {
            if (context.loadedKeyStore().containerType() != KeyStoreContainerType.JKS) {
                return new AssessmentFinding(
                        id(),
                        title(),
                        AssessmentStatus.NOT_APPLICABLE,
                        Severity.INFO,
                        "Container is not JKS",
                        "Container=" + context.loadedKeyStore().containerType(),
                        "No remediation required.",
                        profile.references());
            }
            if (context.loadedKeyStore().integrityCheckPassed()) {
                return new AssessmentFinding(
                        id(),
                        title(),
                        AssessmentStatus.PASS,
                        Severity.INFO,
                        "JKS integrity check passed",
                        "integrityCheckPassed=true",
                        "No remediation required.",
                        profile.references());
            }
            return new AssessmentFinding(
                    id(),
                    title(),
                    AssessmentStatus.FAIL,
                    Severity.CRITICAL,
                    "JKS integrity check failed",
                    "integrityCheckPassed=false (possible tampering or corruption)",
                    "Do not trust this keystore. Re-load from a trusted source or regenerate.",
                    profile.references());
        }
    }
}