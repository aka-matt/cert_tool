package io.github.certtool.domain.context;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Snapshot of the runtime environment used for FIPS Runtime Provider rules (spec §7).
 *
 * <p>Captures the JVM, OS, the list of installed JCA providers, and the captured-at timestamp.
 * The captured-at timestamp lets reports show exactly when the runtime was observed.
 *
 * <p>Providers are reported in {@link java.security.Security#getProviders()} order.
 */
public record RuntimeEnvironment(
        String jvmVendor,
        String jvmVersion,
        String osName,
        String osArch,
        List<ProviderInfo> providers,
        Instant capturedAt) {

    public RuntimeEnvironment {
        Objects.requireNonNull(jvmVendor, "jvmVendor");
        Objects.requireNonNull(jvmVersion, "jvmVersion");
        Objects.requireNonNull(osName, "osName");
        Objects.requireNonNull(osArch, "osArch");
        Objects.requireNonNull(providers, "providers");
        Objects.requireNonNull(capturedAt, "capturedAt");
        providers = List.copyOf(providers);
    }

    /** True iff a Bouncy Castle FIPS Provider is installed in this runtime. */
    public boolean bcfipsDetected() {
        return providers.stream().anyMatch(p -> p.isFipsProvider() && p.name().toUpperCase().contains("BCFIPS"));
    }

    /**
     * True iff any provider in the runtime has {@code approvedOnlyConfirmed == true}. When no
     * provider has explicitly confirmed Approved-Only Mode, runtime rules must return
     * {@code NOT_ASSESSABLE}.
     */
    public boolean approvedOnlyConfirmed() {
        return providers.stream().anyMatch(ProviderInfo::approvedOnlyConfirmed);
    }
}