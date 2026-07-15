# Truststore File Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow the file-open workflow to load JKS and BCFKS truststores independently of their filename or extension.

**Architecture:** Add a file-input task that delegates to `KeyStoreLoader.loadAutoDetect`, retaining the existing JavaFX background-task boundary and password provider. The composition root exposes that task, and `MainShellController` uses it for selected files; the current container-specific task remains available for selected Base64 input. UI copy and filters identify truststores without treating a suffix as the parser decision.

**Tech Stack:** Java 17, JavaFX, Maven, JUnit 5, AssertJ, Bouncy Castle.

## Global Constraints

- Support JKS and BCFKS only; PKCS#12 remains unsupported.
- Do not infer a selected file's container type from its filename or extension.
- UI must not access `KeyStore`, certificate factories, or providers directly.
- Passwords remain `char[]`, are cleared by the core loader, and must not be logged or persisted.
- File bytes, certificate Base64, and key material must not be added to logs or status messages.
- Keep all loading off the JavaFX Application Thread.

---

### Task 1: Content-detecting file load task

**Files:**
- Create: `app/src/main/java/io/github/certtool/app/task/AutoDetectKeyStoreLoadTask.java`
- Create: `app/src/test/java/io/github/certtool/app/task/AutoDetectKeyStoreLoadTaskTest.java`

**Interfaces:**
- Consumes: `KeyStoreLoader.loadAutoDetect(byte[], PasswordProvider)`.
- Produces: `AutoDetectKeyStoreLoadTask`, a `Task<KeyStoreLoadResult>` constructed with `KeyStoreLoader`, raw file bytes, and `PasswordProvider`.
- Used by: `AppComposition.autoDetectLoadTask(byte[])` in Task 2.

- [x] **Step 1: Write the failing BCFKS-truststore test**

```java
@Test
@DisplayName("call() detects and loads BCFKS truststore bytes")
void callDetectsAndLoadsBcfksTruststoreBytes() throws Exception {
    char[] password = "trustpass".toCharArray();
    X509Certificate cert = CertificateGenerator.selfSigned(
            new X500Principal("CN=trust"), CertificateGenerator.rsaKeyPair(2048),
            "SHA256withRSA", Duration.ofDays(30));
    byte[] bytes = KeyStoreGenerator.toBytes(KeyStoreGenerator.bcfks(password, "root", cert), password);

    KeyStoreLoadResult result = new AutoDetectKeyStoreLoadTask(
            new KeyStoreLoader(), bytes, new FixedPasswordProvider(password, Map.of())).call();

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.container()).isEqualTo(KeyStoreContainerType.BCFKS);
    assertThat(result.entries()).singleElement()
            .extracting(LoadedEntry::entryType)
            .isEqualTo(EntryType.TRUSTED_CERTIFICATE);
}
```

- [x] **Step 2: Run the test to verify it fails because the task does not exist**

Run: `/opt/homebrew/bin/bash ./mvnw test -pl app -am -Dtest=AutoDetectKeyStoreLoadTaskTest`

Expected: compilation failure referring to missing `AutoDetectKeyStoreLoadTask`.

- [x] **Step 3: Implement the minimal task**

```java
public final class AutoDetectKeyStoreLoadTask extends Task<KeyStoreLoadResult> {
    private final KeyStoreLoader loader;
    private final byte[] bytes;
    private final PasswordProvider passwordProvider;

    @Override
    protected KeyStoreLoadResult call() {
        tryMessage("Loading keystore or truststore…");
        KeyStoreLoadResult result = loader.loadAutoDetect(bytes, passwordProvider);
        tryMessage(result.isSuccess() ? "Loaded " + result.entries().size() + " entries." : "Load failed.");
        return result;
    }
}
```

Use the same headless-safe `tryMessage` pattern as `LoadKeyStoreTask`; require all constructor arguments with `Objects.requireNonNull`.

- [x] **Step 4: Run the focused task test to verify it passes**

Run: `/opt/homebrew/bin/bash ./mvnw test -pl app -am -Dtest=AutoDetectKeyStoreLoadTaskTest`

Expected: Maven exits 0 and the BCFKS trusted-certificate result is successful.

- [x] **Step 5: Commit the task and its test**

```bash
git add app/src/main/java/io/github/certtool/app/task/AutoDetectKeyStoreLoadTask.java \
  app/src/test/java/io/github/certtool/app/task/AutoDetectKeyStoreLoadTaskTest.java
git commit -m "feat(app): auto-detect truststore files"
```

### Task 2: Wire the file-open workflow and truststore UI copy

**Files:**
- Modify: `app/src/main/java/io/github/certtool/app/AppComposition.java:143-146`
- Modify: `app/src/main/java/io/github/certtool/app/controller/MainShellController.java:171-172, 669-692`
- Modify: `app/src/test/java/io/github/certtool/app/controller/MainShellControllerTest.java`

**Interfaces:**
- Consumes: `AutoDetectKeyStoreLoadTask` from Task 1.
- Produces: `AppComposition.autoDetectLoadTask(byte[])`, returning a task that detects the selected file content.
- Used by: `MainShellController.onOpenKeyStore()`.

- [x] **Step 1: Write the failing composition test**

Add a test that builds the standard test `AppComposition`, calls `autoDetectLoadTask` with valid BCFKS trusted-certificate bytes, invokes `call()`, and asserts a successful `BCFKS` result. This proves the UI composition path no longer needs a filename-derived container.

```java
assertThat(composition.autoDetectLoadTask(bytes).call().container())
        .isEqualTo(KeyStoreContainerType.BCFKS);
```

- [x] **Step 2: Run the focused test to verify it fails because the factory does not exist**

Run: `/opt/homebrew/bin/bash ./mvnw test -pl app -am -Dtest=MainShellControllerTest`

Expected: compilation failure referring to missing `autoDetectLoadTask(byte[])`.

- [x] **Step 3: Add the composition factory and use it for selected files**

Add this factory to `AppComposition`:

```java
public AutoDetectKeyStoreLoadTask autoDetectLoadTask(byte[] bytes) {
    return new AutoDetectKeyStoreLoadTask(loader, bytes, activePasswordProvider);
}
```

In `MainShellController`:

```java
MenuItem openFile = new MenuItem("Open KeyStore or TrustStore…");
chooser.setTitle("Open KeyStore or TrustStore");
chooser.getExtensionFilters().addAll(
        new FileChooser.ExtensionFilter("JKS and BCFKS stores", "*.jks", "*.bcfks", "*.keystore", "*.truststore"),
        new FileChooser.ExtensionFilter("JKS", "*.jks"),
        new FileChooser.ExtensionFilter("BCFKS", "*.bcfks"),
        new FileChooser.ExtensionFilter("All files", "*.*"));
AutoDetectKeyStoreLoadTask task = composition.autoDetectLoadTask(bytes);
```

Remove the extension-based `KeyStoreContainerType` selection from `onOpenKeyStore()`. Keep the existing success listener, inspection submission, and `ContentEncoding.BINARY` analysis unchanged.

- [x] **Step 4: Run the focused controller test to verify it passes**

Run: `/opt/homebrew/bin/bash ./mvnw test -pl app -am -Dtest=MainShellControllerTest`

Expected: Maven exits 0 and the BCFKS composition-path assertion succeeds.

- [x] **Step 5: Commit the workflow wiring**

```bash
git add app/src/main/java/io/github/certtool/app/AppComposition.java \
  app/src/main/java/io/github/certtool/app/controller/MainShellController.java \
  app/src/test/java/io/github/certtool/app/controller/MainShellControllerTest.java
git commit -m "feat(app): open JKS and BCFKS truststores"
```

### Task 3: Regression validation

**Files:**
- Modify: `docs/superpowers/plans/2026-07-15-truststore-file-support.md` only to mark verified steps complete.

**Interfaces:**
- Consumes: the auto-detecting file task and the file-open composition path from Tasks 1 and 2.
- Produces: verification evidence for the JKS and BCFKS truststore file support.

- [x] **Step 1: Run app and core tests including JKS and BCFKS loader coverage**

Run: `/opt/homebrew/bin/bash ./mvnw test -pl app -am`

Expected: Maven exits 0; `KeyStoreLoaderTest` and the new task/controller tests pass.

- [x] **Step 2: Run required full pre-merge verification**

Run: `/opt/homebrew/bin/bash ./mvnw verify`

Expected: Maven exits 0; formatting, static analysis, tests, coverage, dependency checks, and SBOM generation complete.

- [x] **Step 3: Inspect the final working tree**

Run: `git status --short && git diff --check`

Expected: no unintended changes and no whitespace errors.
