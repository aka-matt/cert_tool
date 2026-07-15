# PKCS12 Truststore Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Load, inspect, assess, and convert JKS, BCFKS, and PKCS12 truststores without mistaking a PKCS12 password for a BCFKS password failure.

**Architecture:** Extend the shared container enum and fixture factory with `PKCS12`. Keep the JKS magic fast-path; for DER bytes, `KeyStoreLoader` makes one password request and probes BCFKS and PKCS12 with cloned password values, selecting the sole successful load. All consumers use the expanded enum; the UI only broadens discovery filters and does not infer format from filenames.

**Tech Stack:** Java 17, JavaFX, JDK `PKCS12` provider, Bouncy Castle, Maven, JUnit 5, AssertJ.

## Global Constraints

- Support JKS, BCFKS, and PKCS12 only.
- Do not infer a selected file's container type from filename or extension.
- A DER file must not be labelled BCFKS before a real BCFKS/PKCS12 load resolves the ambiguity.
- Probe an ambiguous DER store with one prompted store password; clone it per candidate and clear it afterward.
- `trustedCertEntry` must load as `TRUSTED_CERTIFICATE` without requesting a private-key password.
- UI code must not directly access `KeyStore`, certificate factories, or providers.
- Passwords, paths, raw bytes, key material, and full Base64 input must not be logged or persisted.
- Keep file I/O and keystore loading off the JavaFX Application Thread.

---

### Task 1: Model PKCS12 and load ambiguous DER stores

**Files:**
- Modify: `domain/src/main/java/io/github/certtool/domain/keystore/KeyStoreContainerType.java`
- Modify: `test-fixtures/src/main/java/io/github/certtool/testfixtures/KeyStoreGenerator.java`
- Modify: `keystore-core/src/main/java/io/github/certtool/keystorecore/load/KeyStoreLoader.java`
- Modify: `keystore-core/src/test/java/io/github/certtool/keystorecore/load/KeyStoreLoaderTest.java`

**Interfaces:**
- Produces: `KeyStoreContainerType.PKCS12`; `KeyStoreGenerator.pkcs12(char[], String, X509Certificate)`; `KeyStoreLoader.loadAutoDetect(byte[], PasswordProvider)` returning `PKCS12` for valid PKCS12 bytes.
- Consumes: `PasswordProvider.requestStorePassword(StorePasswordRequest)` exactly once for ambiguous DER candidates.

- [ ] **Step 1: Write failing PKCS12 and BCFKS ambiguity tests**

```java
@Test
void autoDetectsPkcs12TrustedCertificate() throws Exception {
    byte[] bytes = KeyStoreGenerator.toBytes(
            KeyStoreGenerator.pkcs12(STORE_PWD, "root", certificate()), STORE_PWD);
    KeyStoreLoadResult result = new KeyStoreLoader().loadAutoDetect(
            bytes, new FixedPasswordProvider(STORE_PWD, Map.of()));
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.container()).isEqualTo(KeyStoreContainerType.PKCS12);
    assertThat(result.entries()).singleElement()
            .extracting(LoadedEntry::entryType).isEqualTo(EntryType.TRUSTED_CERTIFICATE);
}
```

Add a BCFKS auto-detect assertion and a counting `PasswordProvider` assertion that the DER probe requests the store password once.

- [ ] **Step 2: Run the focused core test and verify the PKCS12 case fails**

Run: `/opt/homebrew/bin/bash ./mvnw test -pl keystore-core -am -Dtest=KeyStoreLoaderTest -Dsurefire.failIfNoSpecifiedTests=false`

Expected: failure because `PKCS12` and/or `KeyStoreGenerator.pkcs12` do not exist, or PKCS12 is misclassified as BCFKS.

- [ ] **Step 3: Implement the minimal shared support**

```java
public enum KeyStoreContainerType { JKS, BCFKS, PKCS12 }

private static KeyStore newInstance(KeyStoreContainerType container) throws Exception {
    ensureBouncyCastleRegistered();
    return switch (container) {
        case JKS, PKCS12 -> KeyStore.getInstance(container.name());
        case BCFKS -> KeyStore.getInstance(container.name(), BC_PROVIDER);
    };
}
```

For `loadAutoDetect`, retain direct JKS detection. For DER candidates, acquire one password into a closeable cloning provider, call the existing container-specific `load` for BCFKS and PKCS12, return the unique success, and return a typed ambiguity/unsupported-or-password failure only when neither uniquely loads. Clear the cached password in `close()`.

Add `KeyStoreGenerator.pkcs12` using `KeyStore.getInstance(KeyStoreContainerType.PKCS12.name())`, `load(null, storePassword)`, and `setCertificateEntry`.

- [ ] **Step 4: Run the focused core test and verify it passes**

Run: `/opt/homebrew/bin/bash ./mvnw test -pl keystore-core -am -Dtest=KeyStoreLoaderTest -Dsurefire.failIfNoSpecifiedTests=false`

Expected: PKCS12 and BCFKS trusted-certificate auto-detection pass and only one store-password request is observed.

- [ ] **Step 5: Commit the core support**

```bash
git add domain/src/main/java/io/github/certtool/domain/keystore/KeyStoreContainerType.java \
  test-fixtures/src/main/java/io/github/certtool/testfixtures/KeyStoreGenerator.java \
  keystore-core/src/main/java/io/github/certtool/keystorecore/load/KeyStoreLoader.java \
  keystore-core/src/test/java/io/github/certtool/keystorecore/load/KeyStoreLoaderTest.java
git commit -m "feat(core): load PKCS12 truststores"
```

### Task 2: Wire PKCS12 into UI input and conversion

**Files:**
- Modify: `app/src/main/java/io/github/certtool/app/controller/MainShellController.java`
- Modify: `app/src/main/java/io/github/certtool/app/task/PasteBase64LoadTask.java`
- Modify: `conversion/src/main/java/io/github/certtool/conversion/core/KeystoreConversion.java`
- Modify: `test-fixtures/src/main/java/io/github/certtool/testfixtures/KeyStoreGenerator.java`
- Modify: `app/src/test/java/io/github/certtool/app/controller/MainShellControllerTest.java`
- Modify: `app/src/test/java/io/github/certtool/app/task/PasteBase64LoadTaskTest.java`

**Interfaces:**
- Consumes: `KeyStoreContainerType.PKCS12` from Task 1.
- Produces: file filters for `*.p12`/`*.pfx`, paste auto-probing including PKCS12, and conversion factory support through the enum.

- [ ] **Step 1: Write failing UI regressions**

```java
assertThat(result.container()).isEqualTo(KeyStoreContainerType.PKCS12);
assertThat(probedContainers)
        .containsExactly(KeyStoreContainerType.JKS, KeyStoreContainerType.BCFKS, KeyStoreContainerType.PKCS12);
```

Add a selected-file test that writes generated PKCS12 truststore bytes to a `.p12` temporary file and asserts a successful `TRUSTED_CERTIFICATE` result.

- [ ] **Step 2: Run focused app tests and verify failure before UI changes**

Run: `/opt/homebrew/bin/bash ./mvnw test -pl app -am -Dtest=MainShellControllerTest,PasteBase64LoadTaskTest -Dsurefire.failIfNoSpecifiedTests=false`

Expected: PKCS12 assertions fail or the third candidate is absent.

- [ ] **Step 3: Implement UI and conversion wiring**

```java
new FileChooser.ExtensionFilter(
        "JKS, BCFKS, and PKCS12 stores",
        "*.jks", "*.bcfks", "*.keystore", "*.truststore", "*.p12", "*.pfx");
new FileChooser.ExtensionFilter("PKCS12", "*.p12", "*.pfx");
```

Extend the paste probe candidate list with `PKCS12` and choose its sole success using the same reusable password cache. Extend all exhaustive `switch` expressions and fixture `newInstance` methods so PKCS12 uses the JDK provider, while BCFKS remains the only Bouncy-Castle-provider branch.

- [ ] **Step 4: Run focused app and conversion tests**

Run: `/opt/homebrew/bin/bash ./mvnw test -pl app,conversion -am -Dtest=MainShellControllerTest,PasteBase64LoadTaskTest,KeystoreConversionTest -Dsurefire.failIfNoSpecifiedTests=false`

Expected: selected-file and pasted PKCS12 truststores load as `PKCS12`; existing conversion tests remain green.

- [ ] **Step 5: Commit integration support**

```bash
git add app/src/main/java/io/github/certtool/app/controller/MainShellController.java \
  app/src/main/java/io/github/certtool/app/task/PasteBase64LoadTask.java \
  conversion/src/main/java/io/github/certtool/conversion/core/KeystoreConversion.java \
  test-fixtures/src/main/java/io/github/certtool/testfixtures/KeyStoreGenerator.java \
  app/src/test/java/io/github/certtool/app/controller/MainShellControllerTest.java \
  app/src/test/java/io/github/certtool/app/task/PasteBase64LoadTaskTest.java
git commit -m "feat(app): accept PKCS12 truststores"
```

### Task 3: Full validation

- [ ] **Step 1: Run all affected reactor tests**

Run: `/opt/homebrew/bin/bash ./mvnw test -pl app,conversion -am`

Expected: all modules succeed; PKCS12, BCFKS, and JKS tests pass.

- [ ] **Step 2: Run required pre-merge verification**

Run: `/opt/homebrew/bin/bash ./mvnw verify`

Expected: all ten modules complete successfully.

- [ ] **Step 3: Inspect final changes**

Run: `git status --short && git diff --check`

Expected: no unexpected changes or whitespace errors.
