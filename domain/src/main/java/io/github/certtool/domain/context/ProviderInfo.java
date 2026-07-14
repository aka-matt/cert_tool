package io.github.certtool.domain.context;

import java.util.Objects;

/**
 * Information about a single JCA security provider visible in the runtime. Used by
 * {@link RuntimeEnvironment} and reported by Runtime Provider rules.
 *
 * <p>{@code fipsLabel} is an opaque string the provider exposes via {@code Provider.getInfo()}
 * (e.g. {@code "BouncyCastle FIPS Provider"} or {@code "BouncyCastle Security Provider"}). Rules
 * use it to distinguish regular Bouncy Castle from the FIPS build — see spec §6.
 *
 * <p>{@code approvedOnlyKnown} / {@code approvedOnlyConfirmed} capture the BC-FIPS Approved-Only
 * Mode detection state. Per spec §7, when Approved-Only Mode cannot be confirmed reliably, the
 * engine returns {@code NOT_ASSESSABLE} rather than guessing.
 */
public record ProviderInfo(
        String name,
        String version,
        String fipsLabel,
        boolean approvedOnlyKnown,
        boolean approvedOnlyConfirmed) {

    public ProviderInfo {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(fipsLabel, "fipsLabel");
        version = version == null ? "" : version;
    }

    /** True iff this provider self-identifies as a FIPS provider. */
    public boolean isFipsProvider() {
        return fipsLabel.toLowerCase().contains("fips");
    }
}