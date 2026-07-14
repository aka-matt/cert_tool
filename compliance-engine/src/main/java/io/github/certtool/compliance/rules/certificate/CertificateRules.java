package io.github.certtool.compliance.rules.certificate;

import io.github.certtool.compliance.core.Rule;
import io.github.certtool.compliance.rules.RuleCategory;
import io.github.certtool.domain.assessment.AssessmentFinding;
import io.github.certtool.domain.assessment.AssessmentStatus;
import io.github.certtool.domain.assessment.Severity;
import io.github.certtool.domain.certificate.CertificateAnalysis;
import io.github.certtool.domain.certificate.ValidityState;
import io.github.certtool.domain.context.EntryAnalysis;
import io.github.certtool.domain.context.RuleContext;
import io.github.certtool.domain.profile.ExpirationPolicy;
import io.github.certtool.domain.profile.Profile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Built-in Certificate rules (spec §7.3). */
public final class CertificateRules {

    private CertificateRules() {}

    /** Returns all built-in certificate rules in a fixed order. */
    public static List<Rule> all() {
        return List.of(
                new ExpiredCertificateRule(),
                new NotYetValidCertificateRule(),
                new UnknownCriticalExtensionRule());
    }

    static List<CertView> certs(RuleContext context) {
        List<CertView> out = new ArrayList<>();
        for (EntryAnalysis e : context.entries()) {
            if (e.certificate() != null) {
                out.add(new CertView(e.alias(), e.certificate()));
            }
        }
        return out;
    }

    record CertView(String alias, CertificateAnalysis certificate) {}

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

    /** Flags certificates whose notAfter lies in the past. */
    public static final class ExpiredCertificateRule implements Rule {
        @Override
        public String id() {
            return "CERT.EXPIRED";
        }

        @Override
        public String title() {
            return "No certificate is expired";
        }

        @Override
        public RuleCategory category() {
            return RuleCategory.CERTIFICATE;
        }

        @Override
        public AssessmentFinding evaluate(RuleContext context, Profile profile) {
            List<String> expired = new ArrayList<>();
            for (CertView v : certs(context)) {
                if (v.certificate().currentValidity() == ValidityState.EXPIRED) {
                    expired.add(v.alias());
                }
            }
            if (expired.isEmpty()) {
                return finding(this, profile, AssessmentStatus.PASS, Severity.INFO,
                        "No certificates are expired.", "No remediation required.");
            }
            String evidence = "Expired aliases: " + expired;
            return switch (profile.expirationPolicy()) {
                case FAIL -> finding(this, profile, AssessmentStatus.FAIL, Severity.HIGH, evidence,
                        "Renew the affected certificates.");
                case WARN -> finding(this, profile, AssessmentStatus.WARNING, Severity.MEDIUM, evidence,
                        "Renew the affected certificates.");
                case ALLOW -> finding(this, profile, AssessmentStatus.PASS, Severity.INFO, evidence,
                        "Profile permits expired certificates.");
            };
        }
    }

    /** Flags certificates whose notBefore lies in the future. */
    public static final class NotYetValidCertificateRule implements Rule {
        @Override
        public String id() {
            return "CERT.NOT_YET_VALID";
        }

        @Override
        public String title() {
            return "No certificate is not-yet-valid";
        }

        @Override
        public RuleCategory category() {
            return RuleCategory.CERTIFICATE;
        }

        @Override
        public AssessmentFinding evaluate(RuleContext context, Profile profile) {
            List<String> nyv = new ArrayList<>();
            for (CertView v : certs(context)) {
                if (v.certificate().currentValidity() == ValidityState.NOT_YET_VALID) {
                    nyv.add(v.alias());
                }
            }
            if (nyv.isEmpty()) {
                return finding(this, profile, AssessmentStatus.PASS, Severity.INFO,
                        "No certificates are not-yet-valid.", "No remediation required.");
            }
            return finding(this, profile, AssessmentStatus.WARNING, Severity.MEDIUM,
                    "Not-yet-valid aliases: " + nyv,
                    "Verify the system clock is correct, or wait until the certificate's notBefore.");
        }
    }

    /**
     * Flags certificates that carry critical extensions the parser does not recognise. RFC 5280
     * requires that implementations reject such certificates; we therefore FAIL the assessment.
     */
    public static final class UnknownCriticalExtensionRule implements Rule {
        @Override
        public String id() {
            return "CERT.UNRECOGNIZED_CRITICAL_EXTENSION";
        }

        @Override
        public String title() {
            return "No unrecognised critical extensions";
        }

        @Override
        public RuleCategory category() {
            return RuleCategory.CERTIFICATE;
        }

        @Override
        public AssessmentFinding evaluate(RuleContext context, Profile profile) {
            List<String> offenders = new ArrayList<>();
            for (CertView v : certs(context)) {
                var u = v.certificate().extensions().unrecognizedCriticalOids();
                if (!u.isEmpty()) {
                    offenders.add(v.alias() + "=" + String.join(",", u));
                }
            }
            if (offenders.isEmpty()) {
                return finding(this, profile, AssessmentStatus.PASS, Severity.INFO,
                        "No unrecognised critical extensions.",
                        "No remediation required.");
            }
            return finding(this, profile, AssessmentStatus.FAIL, Severity.HIGH,
                    "Aliases with unrecognised critical extensions: " + offenders,
                    "Remove or replace the certificate; clients may fail to validate it.");
        }
    }
}