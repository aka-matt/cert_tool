# PKCS12 truststore support design

## Scope

Add PKCS12 (`.p12` and `.pfx`) support to the existing JKS/BCFKS store-open, inspection,
assessment, and conversion workflows. A PKCS12 truststore's `trustedCertEntry` values remain
ordinary `TRUSTED_CERTIFICATE` loaded entries. No separate truststore data model is introduced.

## Root cause

PKCS12 and BCFKS are both DER-encoded and normally start with ASN.1 sequence byte `0x30`.
The existing detector treats every such byte stream as BCFKS. When a PKCS12 truststore is loaded
through the BCFKS provider, a valid PKCS12 password is reported as wrong or corrupted before any
entry is enumerated.

## Design

`KeyStoreContainerType` gains `PKCS12`. JKS continues to be identified directly from its unique
magic value. DER inputs are an ambiguous candidate set: the loader asks for the store password
once, then tries BCFKS and PKCS12 with independent clones of that password. Exactly one successful
load selects the container; if both fail, the typed failure preserves the normal password/corrupt
classification without claiming the file is BCFKS solely from its first byte.

The JDK `PKCS12` provider loads PKCS12; BCFKS continues to use Bouncy Castle. Enumeration continues
to test `KeyStore.isCertificateEntry(alias)` before key-entry handling, so no private-key password
is requested for a pure truststore.

The open dialog will include `.p12` and `.pfx` filters and identify all supported store types.
The all-files fallback remains, and parsing continues to depend on bytes rather than extension.

## Security and errors

The one prompted password is retained only for the detection operation, cloned for each candidate
load, and cleared after all attempts. No password, file bytes, selected path, certificate Base64,
or key material is shown in logs, status text, or typed messages. Missing/unreadable-file handling
remains unchanged.

## Tests

Tests will generate a local PKCS12 trusted-certificate store and prove byte- and selected-file
auto-detection load it as `PKCS12` with `TRUSTED_CERTIFICATE` entries. They will prove an actual
BCFKS store still selects BCFKS despite DER ambiguity, verify a wrong PKCS12 password remains a
typed store-password failure, and cover `.p12/.pfx` file-selection filters as appropriate.
