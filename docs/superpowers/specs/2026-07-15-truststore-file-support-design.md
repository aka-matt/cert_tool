# Truststore file support design

## Scope

The application will accept JKS and BCFKS truststore files in the same file-open flow as
keystores. PKCS#12 is explicitly out of scope.

## Problem

The file-open flow currently derives the container type from the selected file name: only a
`.bcfks` suffix selects BCFKS; every other suffix selects JKS. A BCFKS truststore named
`.truststore`, with no suffix, or with another conventional truststore name is consequently
loaded as JKS and reported as corrupted or unsupported.

## Design

The file-open path will use the existing `KeyStoreLoader.loadAutoDetect` API. It determines the
JKS or BCFKS container from the file bytes, so file names do not influence the crypto parser.
The existing loader continues to enumerate certificate entries as `TRUSTED_CERTIFICATE`; no
separate truststore model or password flow is required.

The user-facing file command and chooser title will say “KeyStore or TrustStore”. Its filters
will list the supported JKS/BCFKS and common truststore filename patterns, plus an all-files
filter so extension-less conventional truststores remain selectable. The selected file is still
read locally and analysed using the existing background task, password provider, inspection,
assessment, and conversion workflows.

## Error handling

An unsupported or malformed file continues to return the existing typed load failure. A valid
BCFKS truststore must no longer fail merely because its name lacks a `.bcfks` suffix. No
password, file bytes, certificate Base64, or key material is added to logs or status messages.

## Tests

Tests will cover the file-input container selection with BCFKS bytes named as a truststore and
with JKS bytes, proving content detection rather than filename inference. Existing core loader
tests already establish trusted-certificate entry handling and will remain unchanged unless a
small focused test is needed for the new file-input task factory.
