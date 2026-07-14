package io.github.certtool.domain.certificate;

import java.time.Instant;
import java.util.Objects;

/**
 * X.509 validity window. Immutable value object with a {@link #validAt(Instant)} helper that
 * returns the current {@link ValidityState}.
 *
 * <p>Per spec §5: every parsed X.509 certificate exposes a {@code notBefore} and {@code notAfter}
 * with the corresponding current validity state.
 */
public record ValidityWindow(Instant notBefore, Instant notAfter) {

    public ValidityWindow {
        Objects.requireNonNull(notBefore, "notBefore");
        Objects.requireNonNull(notAfter, "notAfter");
        if (!notAfter.isAfter(notBefore)) {
            throw new IllegalArgumentException(
                    "notAfter (" + notAfter + ") must be after notBefore (" + notBefore + ")");
        }
    }

    /**
     * @return {@link ValidityState#VALID} if {@code instant} is within {@code [notBefore, notAfter]},
     *         {@link ValidityState#NOT_YET_VALID} if before, {@link ValidityState#EXPIRED} if after.
     */
    public ValidityState validAt(Instant instant) {
        Objects.requireNonNull(instant, "instant");
        if (instant.isBefore(notBefore)) {
            return ValidityState.NOT_YET_VALID;
        }
        if (instant.isAfter(notAfter)) {
            return ValidityState.EXPIRED;
        }
        return ValidityState.VALID;
    }
}