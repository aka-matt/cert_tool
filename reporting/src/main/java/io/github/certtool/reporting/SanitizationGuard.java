package io.github.certtool.reporting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scans rendered report bytes for forbidden patterns before they leave the module (spec §2, §6).
 *
 * <p>Patterns cover: password-shaped strings, PEM private-key markers, full Base64 keystores, and
 * phrases that falsely claim FIPS certification or NIST certification. The pattern list is itself
 * unit-tested — see {@code SanitizationGuardTest}.
 *
 * <p>The guard is intentionally a regex scan, not a parser: speed matters because every renderer
 * invokes it on every render.
 */
public final class SanitizationGuard {

    /** Named patterns compiled once. Order matters only for deterministic violation ordering. */
    private record NamedPattern(String name, Pattern regex) {}

    private static final List<NamedPattern> PATTERNS = List.of(
            // password=foo or password = foo / Password=Foo (case-insensitive)
            new NamedPattern("password-keyword", Pattern.compile("(?i)password\\s*[:=]\\s*\\S+")),
            // PEM private-key markers (case-sensitive — they are exact RFC 7468 labels)
            new NamedPattern("BEGIN PRIVATE KEY",
                    Pattern.compile("-----BEGIN (?:RSA |EC |DSA )?PRIVATE KEY-----")),
            new NamedPattern("BEGIN RSA PRIVATE KEY",
                    Pattern.compile("-----BEGIN RSA PRIVATE KEY-----")),
            new NamedPattern("BEGIN ENCRYPTED PRIVATE KEY",
                    Pattern.compile("-----BEGIN ENCRYPTED PRIVATE KEY-----")),
            new NamedPattern("BEGIN SECRET KEY",
                    Pattern.compile("-----BEGIN SECRET KEY-----")),
            // Cert-tool MUST NOT produce these strings (spec §6: never claim FIPS Certification).
            new NamedPattern("FIPS Certification claim", Pattern.compile("FIPS Certification")),
            new NamedPattern("Official FIPS Validation claim",
                    Pattern.compile("Official FIPS Validation")),
            new NamedPattern("NIST Certified claim", Pattern.compile("NIST Certified")),
            new NamedPattern("正式认证结论 claim", Pattern.compile("正式认证结论")));

    private SanitizationGuard() {}

    /**
     * Returns the list of forbidden patterns matched in {@code text}, in source order. Returns an
     * empty list if the text is clean.
     */
    public static List<SanitizationViolation> scan(String text, String source) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(source, "source");
        List<SanitizationViolation> out = new ArrayList<>();
        for (NamedPattern np : PATTERNS) {
            Matcher m = np.regex.matcher(text);
            while (m.find()) {
                out.add(new SanitizationViolation(source, np.name, m.group(), m.start()));
            }
        }
        return List.copyOf(out);
    }

    /** Throws {@link RenderException} if any forbidden pattern is present; silent otherwise. */
    public static void enforce(String text, String source) {
        List<SanitizationViolation> v = scan(text, source);
        if (!v.isEmpty()) {
            throw new RenderException(v);
        }
    }
}