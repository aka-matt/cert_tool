package io.github.certtool.domain.certificate;

import java.security.PublicKey;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECParameterSpec;
import java.util.Objects;

/**
 * Immutable, descriptive facts about a certificate's public key. Pure JDK-only.
 *
 * <p>Per spec §5: each parsed certificate must expose the public key algorithm, RSA key size
 * (when applicable), DSA parameters (when applicable), and EC curve name / OID (when applicable).
 *
 * <p>This type is descriptive only — it does NOT decide whether a key size is "weak" or whether
 * a curve is "approved." Phase 4's rule engine maps these facts into findings.
 */
public record PublicKeyInfo(
        KeyAlgorithm algorithm,
        Integer rsaKeySize,
        String ecCurveName,
        String ecCurveOid,
        String dsaParameters) {

    public PublicKeyInfo {
        Objects.requireNonNull(algorithm, "algorithm");
    }

    /** Factory: derive {@link PublicKeyInfo} from a {@link PublicKey}. */
    public static PublicKeyInfo of(PublicKey key) {
        Objects.requireNonNull(key, "key");
        if (key instanceof RSAPublicKey rsa) {
            return new PublicKeyInfo(KeyAlgorithm.RSA, rsa.getModulus().bitLength(), null, null, null);
        }
        if (key instanceof ECPublicKey ec) {
            ECParameterSpec params = ec.getParams();
            String curveName = nameOf(params);
            String curveOid = oidOf(params);
            return new PublicKeyInfo(KeyAlgorithm.EC, null, curveName, curveOid, null);
        }
        if (key instanceof DSAPublicKey dsa) {
            return new PublicKeyInfo(
                    KeyAlgorithm.DSA, null, null, null, describeDsa(dsa.getParams()));
        }
        return new PublicKeyInfo(KeyAlgorithm.UNKNOWN, null, null, null, null);
    }

    private static String nameOf(ECParameterSpec params) {
        if (params == null) {
            return null;
        }
        // Both SunEC's and BC's ECNamedCurveSpec expose the friendly curve name via getName().
        // We probe by class name so domain stays free of any cryptographic deps.
        String cn = params.getClass().getName();
        if (cn.endsWith(".ECNamedCurveSpec")) {
            try {
                Object name = params.getClass().getMethod("getName").invoke(params);
                return name == null ? null : name.toString();
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String oidOf(ECParameterSpec params) {
        // Standard JDK ECParameterSpec carries no OID. BC exposes it via ASN.1; the analyzer
        // module is the right place to enrich this record with BC's ASN.1 decoding if needed.
        // Domain stays portable.
        return null;
    }

    private static String describeDsa(java.security.interfaces.DSAParams params) {
        if (params == null) {
            return null;
        }
        return "p=" + params.getP().bitLength() + "-bits";
    }
}