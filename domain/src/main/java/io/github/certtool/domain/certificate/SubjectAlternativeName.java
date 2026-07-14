package io.github.certtool.domain.certificate;

import java.util.Objects;

/**
 * Subject Alternative Name (SAN) entry. Per RFC 5280, GeneralName types 1-7 cover the common cases
 * (rfc822Name, dNSName, x400Address, directoryName, ediPartyName, uniformResourceIdentifier,
 * iPAddress); registeredID is type 8. We expose the integer type code so the UI can render the
 * matching icon/label.
 */
public record SubjectAlternativeName(int generalNameType, String value) {

    public SubjectAlternativeName {
        Objects.requireNonNull(value, "value");
    }

    /** RFC 5280 GeneralName type codes. */
    public static final int TYPE_RFC822_NAME = 1;
    public static final int TYPE_DNS_NAME = 2;
    public static final int TYPE_DIRECTORY_NAME = 4;
    public static final int TYPE_URI = 6;
    public static final int TYPE_IP_ADDRESS = 7;
    public static final int TYPE_REGISTERED_ID = 8;
}