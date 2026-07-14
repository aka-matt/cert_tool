package io.github.certtool.compliance.loader;

import io.github.certtool.domain.profile.Profile;
import io.github.certtool.domain.profile.ProfileId;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;

/** Static accessor for the built-in profile JSON resources. */
public final class DefaultProfiles {

    /** Resource path for the FIPS 140-3 (current standard) profile. */
    public static final String FIPS_140_3_PATH = "/io/github/certtool/compliance/profiles/FIPS_140_3_ASSESSMENT.json";

    /** Resource path for the FIPS 140-2 legacy profile. */
    public static final String FIPS_140_2_LEGACY_PATH =
            "/io/github/certtool/compliance/profiles/FIPS_140_2_LEGACY_ASSESSMENT.json";

    private DefaultProfiles() {}

    /** Returns an {@link InputStream} for the FIPS 140-3 default profile JSON. */
    public static InputStream fips1403() {
        InputStream in = DefaultProfiles.class.getResourceAsStream(FIPS_140_3_PATH);
        Objects.requireNonNull(in, "Missing resource: " + FIPS_140_3_PATH);
        return in;
    }

    /** Returns an {@link InputStream} for the FIPS 140-2 legacy profile JSON. */
    public static InputStream fips1402Legacy() {
        InputStream in = DefaultProfiles.class.getResourceAsStream(FIPS_140_2_LEGACY_PATH);
        Objects.requireNonNull(in, "Missing resource: " + FIPS_140_2_LEGACY_PATH);
        return in;
    }

    /** Loads and parses the FIPS 140-3 default profile. */
    public static Profile loadFips1403() throws IOException {
        try (InputStream in = fips1403()) {
            return ProfileLoader.fromJson(in);
        }
    }

    /** Loads and parses the FIPS 140-2 legacy default profile. */
    public static Profile loadFips1402Legacy() throws IOException {
        try (InputStream in = fips1402Legacy()) {
            return ProfileLoader.fromJson(in);
        }
    }

    /** Loads the built-in profile by {@link ProfileId}. */
    public static Profile load(ProfileId id) throws IOException {
        return switch (id) {
            case FIPS_140_3_ASSESSMENT -> loadFips1403();
            case FIPS_140_2_LEGACY_ASSESSMENT -> loadFips1402Legacy();
            case CUSTOM -> throw new IllegalArgumentException(
                    "CUSTOM profiles must be supplied by the user; no built-in resource.");
        };
    }

    /**
     * Returns every built-in profile in display order. Failures to load a profile propagate as
     * {@link IOException} — callers should not silently drop a profile.
     */
    public static List<Profile> all() throws IOException {
        return List.of(loadFips1402Legacy(), loadFips1403());
    }
}