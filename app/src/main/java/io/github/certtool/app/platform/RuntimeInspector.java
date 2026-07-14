package io.github.certtool.app.platform;

import io.github.certtool.domain.context.ProviderInfo;
import io.github.certtool.domain.context.RuntimeEnvironment;
import java.security.Provider;
import java.security.Security;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Static helper that captures a {@link RuntimeEnvironment} from the live JVM. Used by the
 * Runtime page to display JVM/provider info and by FIPS Runtime Provider rules.
 *
 * <p>Providers are reported in {@link Security#getProviders()} order; {@code approvedOnlyKnown}
 * is conservatively {@code false} when the BCFIPS provider is not present, so the rules return
 * {@code NOT_ASSESSABLE} rather than guessing.
 */
public final class RuntimeInspector {

    private RuntimeInspector() {}

    /**
     * Captures the current JVM's runtime environment.
     *
     * @return a populated {@link RuntimeEnvironment}
     */
    public static RuntimeEnvironment capture() {
        String jvmVendor = System.getProperty("java.vendor", "<unknown>");
        String jvmVersion = System.getProperty("java.version", "<unknown>");
        String osName = System.getProperty("os.name", "<unknown>");
        String osArch = System.getProperty("os.arch", "<unknown>");

        List<ProviderInfo> providers = new ArrayList<>();
        for (java.security.Provider p : Security.getProviders()) {
            String name = p.getName();
            String version = String.valueOf(p.getVersion());
            String info = p.getInfo();
            String label = info == null ? "" : info;
            boolean fipsKnown = label.toLowerCase().contains("fips");
            boolean approved = isApprovedOnly(p, label);
            providers.add(new ProviderInfo(name, version, label, fipsKnown, approved));
        }
        return new RuntimeEnvironment(jvmVendor, jvmVersion, osName, osArch,
                providers, Instant.now());
    }

    private static boolean isApprovedOnly(Provider p, String label) {
        String cls = p.getClass().getName();
        if (cls.toLowerCase().contains("approved")) {
            return true;
        }
        if (p.get("ApprovedOnlyMode") != null || p.get("org.bouncycastle.fips.approved_only") != null) {
            return true;
        }
        return label.toLowerCase().contains("approved");
    }
}
