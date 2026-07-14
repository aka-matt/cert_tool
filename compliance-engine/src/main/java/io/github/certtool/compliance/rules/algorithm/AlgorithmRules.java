package io.github.certtool.compliance.rules.algorithm;

import io.github.certtool.compliance.core.Rule;
import io.github.certtool.compliance.rules.RuleCategory;
import io.github.certtool.domain.assessment.AssessmentFinding;
import io.github.certtool.domain.assessment.AssessmentStatus;
import io.github.certtool.domain.assessment.Severity;
import io.github.certtool.domain.certificate.CertificateAnalysis;
import io.github.certtool.domain.context.EntryAnalysis;
import io.github.certtool.domain.context.RuleContext;
import io.github.certtool.domain.profile.Profile;
import io.github.certtool.domain.profile.Sha1Policy;
import io.github.certtool.domain.profile.UnknownAlgorithmPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Built-in Algorithm rules (spec §7.4). */
public final class AlgorithmRules {

    private AlgorithmRules() {}

    /** Returns all built-in algorithm rules in a fixed order. */
    public static List<Rule> all() {
        return List.of(
                new WeakRsaKeyRule(),
                new Sha1SignatureRule(),
                new Md5SignatureRule(),
                new UnknownSignatureRule());
    }

    /** Walks every parsed certificate in the context and returns the matched findings. */
    static List<CertificateView> certs(RuleContext context) {
        List<CertificateView> out = new ArrayList<>();
        for (EntryAnalysis e : context.entries()) {
            if (e.certificate() != null) {
                out.add(new CertificateView(e.alias(), e.certificate()));
            }
        }
        return out;
    }

    /** Lightweight pair of (alias, certificate) for rule evidence strings. */
    record CertificateView(String alias, CertificateAnalysis certificate) {}

    /** Helper that returns true iff the algorithm name contains the given digest name (case-insensitive). */
    static boolean isAlg(String algorithm, String digest) {
        if (algorithm == null) {
            return false;
        }
        return algorithm.toUpperCase(Locale.ROOT).contains(digest.toUpperCase(Locale.ROOT));
    }

    // ---------------------------------------------------------------------------------------------
    // Rules
    // ---------------------------------------------------------------------------------------------

    /** Flags RSA keys smaller than the profile's minimum. */
    public static final class WeakRsaKeyRule implements Rule {
        @Override
        public String id() {
            return "ALGORITHM.RSA_KEY_SIZE";
        }

        @Override
        public String title() {
            return "RSA key size meets profile minimum";
        }

        @Override
        public RuleCategory category() {
            return RuleCategory.ALGORITHM;
        }

        @Override
        public AssessmentFinding evaluate(RuleContext context, Profile profile) {
            Integer min = profile.minimumKeySize("RSA");
            if (min == null) {
                return finding(this, profile, AssessmentStatus.NOT_ASSESSABLE, Severity.INFO,
                        "Profile does not configure an RSA minimum.", "No remediation required.");
            }
            int worstBits = Integer.MAX_VALUE;
            String worstAlias = null;
            for (CertificateView v : certs(context)) {
                Integer bits = v.certificate().publicKeyInfo().rsaKeySize();
                if (bits != null && bits < worstBits) {
                    worstBits = bits;
                    worstAlias = v.alias();
                }
            }
            if (worstBits == Integer.MAX_VALUE) {
                return finding(this, profile, AssessmentStatus.NOT_APPLICABLE, Severity.INFO,
                        "No RSA keys present.", "No remediation required.");
            }
            if (worstBits < min) {
                return finding(this, profile, AssessmentStatus.FAIL, Severity.HIGH,
                        "Smallest RSA key in keystore is " + worstBits + " bits (alias=" + worstAlias
                                + "), below profile minimum " + min + ".",
                        "Regenerate the key with at least " + min + "-bit RSA.");
            }
            return finding(this, profile, AssessmentStatus.PASS, Severity.INFO,
                    "Smallest RSA key is " + worstBits + " bits (alias=" + worstAlias
                            + "), meets profile minimum " + min + ".",
                    "No remediation required.");
        }
    }

    /** Flags SHA-1 signatures according to profile policy. */
    public static final class Sha1SignatureRule implements Rule {
        @Override
        public String id() {
            return "ALGORITHM.SHA1_SIGNATURE";
        }

        @Override
        public String title() {
            return "Signature algorithm is not SHA-1";
        }

        @Override
        public RuleCategory category() {
            return RuleCategory.ALGORITHM;
        }

        @Override
        public AssessmentFinding evaluate(RuleContext context, Profile profile) {
            List<String> sha1Aliases = new ArrayList<>();
            for (CertificateView v : certs(context)) {
                if (isAlg(v.certificate().signatureAlgorithm(), "SHA1")) {
                    sha1Aliases.add(v.alias());
                }
            }
            if (sha1Aliases.isEmpty()) {
                return finding(this, profile, AssessmentStatus.PASS, Severity.INFO,
                        "No SHA-1-signed certificates found.", "No remediation required.");
            }
            String evidence = "SHA-1-signed aliases: " + sha1Aliases;
            return switch (profile.sha1Policy()) {
                case DISALLOW -> finding(this, profile, AssessmentStatus.FAIL, Severity.HIGH, evidence,
                        "Re-sign with SHA-256 or stronger; SHA-1 is disallowed for digital signatures.");
                case WARN -> finding(this, profile, AssessmentStatus.WARNING, Severity.MEDIUM, evidence,
                        "SHA-1 is deprecated; re-sign with SHA-256 or stronger when feasible.");
                case ALLOW -> finding(this, profile, AssessmentStatus.PASS, Severity.INFO, evidence,
                        "Profile permits SHA-1.");
            };
        }
    }

    /** Flags MD5 signatures unconditionally (always disallowed). */
    public static final class Md5SignatureRule implements Rule {
        @Override
        public String id() {
            return "ALGORITHM.MD5_SIGNATURE";
        }

        @Override
        public String title() {
            return "Signature algorithm is not MD5";
        }

        @Override
        public RuleCategory category() {
            return RuleCategory.ALGORITHM;
        }

        @Override
        public AssessmentFinding evaluate(RuleContext context, Profile profile) {
            List<String> md5Aliases = new ArrayList<>();
            for (CertificateView v : certs(context)) {
                if (isAlg(v.certificate().signatureAlgorithm(), "MD5")) {
                    md5Aliases.add(v.alias());
                }
            }
            if (md5Aliases.isEmpty()) {
                return finding(this, profile, AssessmentStatus.PASS, Severity.INFO,
                        "No MD5-signed certificates found.", "No remediation required.");
            }
            return finding(this, profile, AssessmentStatus.FAIL, Severity.CRITICAL,
                    "MD5-signed aliases: " + md5Aliases,
                    "Re-sign with SHA-256 or stronger; MD5 is broken.");
        }
    }

    /** Flags signature algorithms that the tool cannot recognise. */
    public static final class UnknownSignatureRule implements Rule {
        @Override
        public String id() {
            return "ALGORITHM.UNKNOWN_SIGNATURE";
        }

        @Override
        public String title() {
            return "Signature algorithm is recognised";
        }

        @Override
        public RuleCategory category() {
            return RuleCategory.ALGORITHM;
        }

        @Override
        public AssessmentFinding evaluate(RuleContext context, Profile profile) {
            List<String> unknowns = new ArrayList<>();
            for (CertificateView v : certs(context)) {
                String alg = v.certificate().signatureAlgorithm();
                if (alg == null || alg.isBlank()) {
                    unknowns.add(v.alias());
                    continue;
                }
                String up = alg.toUpperCase(Locale.ROOT);
                boolean recognised = up.contains("SHA")
                        || up.contains("ECDSA")
                        || up.contains("RSA")
                        || up.contains("ED25519")
                        || up.contains("ED448")
                        || up.contains("DSA");
                if (!recognised) {
                    unknowns.add(v.alias());
                }
            }
            if (unknowns.isEmpty()) {
                return finding(this, profile, AssessmentStatus.PASS, Severity.INFO,
                        "All signature algorithms recognised.", "No remediation required.");
            }
            String evidence = "Unrecognised signature algorithm on aliases: " + unknowns;
            return switch (profile.unknownAlgorithmPolicy()) {
                case FAIL -> finding(this, profile, AssessmentStatus.FAIL, Severity.HIGH, evidence,
                        "Replace with an NIST-approved signature algorithm.");
                case WARNING -> finding(this, profile, AssessmentStatus.WARNING, Severity.MEDIUM, evidence,
                        "Verify the algorithm against your security policy.");
                case NOT_ASSESSABLE -> finding(this, profile, AssessmentStatus.NOT_ASSESSABLE, Severity.INFO, evidence,
                        "Tool cannot assess this algorithm; verify with a qualified reviewer.");
                case ALLOW -> finding(this, profile, AssessmentStatus.PASS, Severity.INFO, evidence,
                        "Profile permits unknown algorithms.");
            };
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

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
}