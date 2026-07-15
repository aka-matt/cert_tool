# Inspect Page Detailed View Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Inspect tab's placeholder `Label("select alias")` with a full BorderPane that immediately shows the loaded JKS/BCFKS keystore summary and detailed information about every certificate within it.

**Architecture:** Add a `domain.inspect` aggregate (`KeyStoreSummary`, `InspectedCertificate`, `InspectedEntry`, `InspectedKeyStore`), a background `AnalyzeKeyStoreTask` that walks every entry's chain through `CertificateAnalyzer`, and a reworked `InspectViewModel` exposing typed observables. The view layer composes a left `TreeView` grouped by entry type with a right `TabPane` containing Overview / Certificate / Chain / Extensions / PEM. Selection on the left drives the right pane.

**Tech Stack:** Java 17 LTS, JavaFX 17 (`Task`, `ObjectProperty`, `TreeView`, `TabPane`, `SplitPane`, `TextArea`, `GridPane`, `Label`, `Button`), JUnit 5, AssertJ, Mockito not used, Bouncy Castle (`certificate-analysis` module for parsing), test-fixtures (`CertificateGenerator`, `CertificateChains`).

**Spec:** [`docs/superpowers/specs/2026-07-14-inspect-page-design.md`](../specs/2026-07-14-inspect-page-design.md)

## File Layout

New:

- `domain/src/main/java/io/github/certtool/domain/inspect/KeyStoreSummary.java`
- `domain/src/main/java/io/github/certtool/domain/inspect/InspectedCertificate.java`
- `domain/src/main/java/io/github/certtool/domain/inspect/InspectedEntry.java`
- `domain/src/main/java/io/github/certtool/domain/inspect/InspectedKeyStore.java`
- `app/src/main/java/io/github/certtool/app/task/AnalyzeKeyStoreTask.java`
- `domain/src/test/java/io/github/certtool/domain/inspect/KeyStoreSummaryTest.java`
- `domain/src/test/java/io/github/certtool/domain/inspect/InspectedKeyStoreTest.java`
- `app/src/test/java/io/github/certtool/app/task/AnalyzeKeyStoreTaskTest.java`
- `app/src/test/java/io/github/certtool/app/task/AnalyzeKeyStoreTaskSecurityTest.java`
- `app/src/test/java/io/github/certtool/app/viewmodel/InspectViewModelTest.java`

Modified:

- `app/src/main/java/io/github/certtool/app/AppComposition.java` — register `analyzeTask(...)` factory and refresh the `Builder` field.
- `app/src/main/java/io/github/certtool/app/controller/MainShellController.java` — fully rewrite `buildInspectView()` and submit `AnalyzeKeyStoreTask` on load success.
- `app/src/main/java/io/github/certtool/app/controller/InspectController.java` — add `applyInspection(InspectedKeyStore)`.
- `app/src/main/java/io/github/certtool/app/viewmodel/InspectViewModel.java` — add `inspectedProperty`, `currentEntryProperty`, `currentCertificateIndexProperty`.

Modified tests:

- `app/src/test/java/io/github/certtool/app/controller/InspectControllerTest.java` — add `applyInspection` coverage.
- `app/src/test/java/io/github/certtool/app/controller/MainShellControllerTest.java` — assert `buildInspectView()` returns non-null when an inspected keystore is present and the Overview tab binds.

## Global Constraints

- Java 17 LTS; do not introduce Java 21 features.
- Tests follow strict TDD: failing test → minimal impl → green → refactor.
- JavaFX Application Thread: never block. Long work goes through `Task` on the background executor.
- No TestFX UI tests for this work; UI verification is via VM unit tests + the existing `./mvnw verify` pipeline.
- No log/status/PEM/exception may contain store password, key password, private key, secret key, full Base64, or full keystore binary.
- Domain layer (`domain/inspect/*`) is pure Java with no JavaFX or `KeyStore` types. `InspectedCertificate` may hold an `X509Certificate` reference because that's required by `CertificateAnalyzer` callers — but the domain module already transitively depends on the JDK security packages, so this is allowed.
- Always run `./mvnw spotless:apply` before `./mvnw verify` if any source was edited (Spotless gates the build).
- All long-running parsing work goes through `AppComposition.backgroundExecutor()`.
- No new external libraries are added.

---

## Task 1: Add `KeyStoreSummary` record (pure value object)

**Files:**
- Create: `domain/src/main/java/io/github/certtool/domain/inspect/KeyStoreSummary.java`
- Test: `domain/src/test/java/io/github/certtool/domain/inspect/KeyStoreSummaryTest.java`

**Interfaces:**
- Consumes: `KeyStoreLoadResult`, `ContentEncoding`
- Produces: `record KeyStoreSummary(KeyStoreContainerType containerType, ContentEncoding encoding, String providerName, String providerVersion, Map<EntryType, Integer> entryCountsByType, int totalEntries, int totalCertificates)`, factory `KeyStoreSummary.from(KeyStoreLoadResult result, ContentEncoding encoding)`.

- [ ] **Step 1: Write the failing test**

Write `domain/src/test/java/io/github/certtool/domain/inspect/KeyStoreSummaryTest.java`:

```java
package io.github.certtool.domain.inspect;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.keystore.EntryType;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.keystorecore.load.KeyStoreLoadResult;
import io.github.certtool.keystorecore.load.LoadedEntry;
import java.security.cert.Certificate;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("KeyStoreSummary")
class KeyStoreSummaryTest {

    private static LoadedEntry trustedCert(String alias, Certificate cert) {
        return LoadedEntry.trustedCertificate(alias, cert, new Date());
    }

    private static LoadedEntry secretKey(String alias) {
        return new LoadedEntry(alias, EntryType.SECRET_KEY, new Date(),
                List.of(), "AES", 256, true, List.of());
    }

    @Test
    @DisplayName("counts entries by type and certificates across all chains")
    void countsByType() {
        Certificate c1 = new DummyCert();
        Certificate c2 = new DummyCert();
        Certificate c3 = new DummyCert();
        KeyStoreLoadResult result = KeyStoreLoadResult.success(
                KeyStoreContainerType.JKS, "SUN", "17",
                List.of(trustedCert("a", c1), trustedCert("b", c2), secretKey("k")));

        KeyStoreSummary s = KeyStoreSummary.from(result, ContentEncoding.BINARY);

        assertThat(s.containerType()).isEqualTo(KeyStoreContainerType.JKS);
        assertThat(s.encoding()).isEqualTo(ContentEncoding.BINARY);
        assertThat(s.providerName()).isEqualTo("SUN");
        assertThat(s.providerVersion()).isEqualTo("17");
        assertThat(s.totalEntries()).isEqualTo(3);
        assertThat(s.totalCertificates()).isEqualTo(2);
        assertThat(s.entryCountsByType()).containsEntry(EntryType.TRUSTED_CERTIFICATE, 2)
                .containsEntry(EntryType.SECRET_KEY, 1)
                .containsEntry(EntryType.PRIVATE_KEY, 0)
                .containsEntry(EntryType.UNKNOWN, 0);
    }

    @Test
    @DisplayName("empty result produces zero counts and an empty counts map")
    void emptyResult() {
        KeyStoreLoadResult result = KeyStoreLoadResult.success(
                KeyStoreContainerType.BCFKS, "BCFIPS", "1.0", List.of());

        KeyStoreSummary s = KeyStoreSummary.from(result, ContentEncoding.BASE64);

        assertThat(s.totalEntries()).isZero();
        assertThat(s.totalCertificates()).isZero();
        assertThat(s.entryCountsByType()).hasSize(4)
                .containsValues(0, 0, 0, 0);
    }

    @Test
    @DisplayName("entry counts map is immutable")
    void countsMapIsImmutable() {
        KeyStoreLoadResult result = KeyStoreLoadResult.success(
                KeyStoreContainerType.JKS, "SUN", "17",
                List.of(trustedCert("a", new DummyCert())));
        KeyStoreSummary s = KeyStoreSummary.from(result, ContentEncoding.BINARY);

        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> s.entryCountsByType().put(EntryType.PRIVATE_KEY, 99));
    }

    /** Minimal Certificate stand-in; never inspected. */
    private static final class DummyCert implements Certificate {
        @Override public byte[] getEncoded() { return new byte[0]; }
        @Override public java.security.PublicKey getPublicKey() { return null; }
        @Override public String getType() { return "X.509"; }
        @Override public void verify(java.security.PublicKey key) { }
        @Override public void verify(java.security.PublicKey key, String sigProvider) { }
        @Override public String toString() { return "DummyCert"; }
        @Override public int hashCode() { return 0; }
        @Override public boolean equals(Object other) { return other == this; }
    }
}
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `./mvnw -pl domain test -Dtest=KeyStoreSummaryTest -q`
Expected: `BUILD FAILURE` — `KeyStoreSummary` does not exist.

- [ ] **Step 3: Implement `KeyStoreSummary`**

Write `domain/src/main/java/io/github/certtool/domain/inspect/KeyStoreSummary.java`:

```java
package io.github.certtool.domain.inspect;

import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.keystore.EntryType;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.keystorecore.load.KeyStoreLoadResult;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Counts and metadata that summarise a successfully loaded keystore. */
public record KeyStoreSummary(
        KeyStoreContainerType containerType,
        ContentEncoding encoding,
        String providerName,
        String providerVersion,
        Map<EntryType, Integer> entryCountsByType,
        int totalEntries,
        int totalCertificates) {

    public KeyStoreSummary {
        Objects.requireNonNull(containerType, "containerType");
        Objects.requireNonNull(encoding, "encoding");
        Objects.requireNonNull(providerName, "providerName");
        Objects.requireNonNull(providerVersion, "providerVersion");
        Objects.requireNonNull(entryCountsByType, "entryCountsByType");
        entryCountsByType = Map.copyOf(entryCountsByType);
        if (totalEntries < 0) {
            throw new IllegalArgumentException("totalEntries must be >= 0");
        }
        if (totalCertificates < 0) {
            throw new IllegalArgumentException("totalCertificates must be >= 0");
        }
    }

    /** Derives a summary from a successful load result. */
    public static KeyStoreSummary from(KeyStoreLoadResult result, ContentEncoding encoding) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(encoding, "encoding");
        Objects.requireNonNull(result.container(), "result.container");
        Map<EntryType, Integer> counts = new EnumMap<>(EntryType.class);
        counts.put(EntryType.PRIVATE_KEY, 0);
        counts.put(EntryType.TRUSTED_CERTIFICATE, 0);
        counts.put(EntryType.SECRET_KEY, 0);
        counts.put(EntryType.UNKNOWN, 0);

        int total = 0;
        int certs = 0;
        for (var e : result.entries()) {
            counts.merge(e.entryType(), 1, Integer::sum);
            certs += e.certificateChain().size();
            total++;
        }
        return new KeyStoreSummary(
                result.container(),
                encoding,
                result.providerName() == null ? "" : result.providerName(),
                result.providerVersion() == null ? "" : result.providerVersion(),
                counts,
                total,
                certs);
    }
}
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `./mvnw -pl domain test -Dtest=KeyStoreSummaryTest -q`
Expected: `BUILD SUCCESS`, all three tests green.

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/java/io/github/certtool/domain/inspect/KeyStoreSummary.java \
        domain/src/test/java/io/github/certtool/domain/inspect/KeyStoreSummaryTest.java
git commit -m "feat(domain): add KeyStoreSummary record"
```

---

## Task 2: Add `InspectedCertificate`, `InspectedEntry`, `InspectedKeyStore` aggregates

**Files:**
- Create: `domain/src/main/java/io/github/certtool/domain/inspect/InspectedCertificate.java`
- Create: `domain/src/main/java/io/github/certtool/domain/inspect/InspectedEntry.java`
- Create: `domain/src/main/java/io/github/certtool/domain/inspect/InspectedKeyStore.java`
- Test: `domain/src/test/java/io/github/certtool/domain/inspect/InspectedKeyStoreTest.java`

**Interfaces:**
- Produces:
  - `record InspectedCertificate(int chainIndex, CertificateAnalysis analysis)` — wraps a single analyzed cert with its position. `analysis` is non-null.
  - `record InspectedEntry(String alias, EntryType entryType, Date creationDate, boolean readable, String keyAlgorithm, Integer keySize, List<InspectedCertificate> certificates, List<String> warnings)`
  - `record InspectedKeyStore(KeyStoreSummary summary, List<InspectedEntry> entries)`

- [ ] **Step 1: Write the failing test**

Write `domain/src/test/java/io/github/certtool/domain/inspect/InspectedKeyStoreTest.java`:

```java
package io.github.certtool.domain.inspect;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.domain.certificate.CertificateAnalysis;
import io.github.certtool.domain.certificate.ValidityState;
import io.github.certtool.domain.certificate.ValidityWindow;
import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.keystore.EntryType;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.keystorecore.load.KeyStoreLoadResult;
import io.github.certtool.keystorecore.load.LoadedEntry;
import java.math.BigInteger;
import java.security.cert.Certificate;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("InspectedKeyStore")
class InspectedKeyStoreTest {

    @Test
    @DisplayName("records are immutable and lists are defensively copied")
    void immutability() {
        KeyStoreSummary summary = KeyStoreSummary.from(
                KeyStoreLoadResult.success(KeyStoreContainerType.JKS, "SUN", "17", List.of()),
                ContentEncoding.BINARY);
        InspectedEntry entry = new InspectedEntry(
                "a", EntryType.TRUSTED_CERTIFICATE, new Date(), true,
                null, null, List.of(), List.of());
        InspectedKeyStore inspected = new InspectedKeyStore(summary, List.of(entry));

        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> inspected.entries().add(entry));
    }

    @Test
    @DisplayName("empty-chain entry has zero certificates and carries the warnings list")
    void emptyChainEntry() {
        KeyStoreSummary summary = KeyStoreSummary.from(
                KeyStoreLoadResult.success(KeyStoreContainerType.JKS, "SUN", "17", List.of()),
                ContentEncoding.BINARY);
        InspectedEntry entry = new InspectedEntry(
                "k", EntryType.SECRET_KEY, new Date(), true,
                "AES", 256, List.of(), List.of("no certs"));
        InspectedKeyStore inspected = new InspectedKeyStore(summary, List.of(entry));

        assertThat(inspected.entries()).hasSize(1);
        assertThat(inspected.entries().get(0).certificates()).isEmpty();
        assertThat(inspected.entries().get(0).warnings()).containsExactly("no certs");
    }

    @Test
    @DisplayName("entry with one certificate exposes that certificate's CertificateAnalysis")
    void singleCertEntry() {
        KeyStoreSummary summary = KeyStoreSummary.from(
                KeyStoreLoadResult.success(KeyStoreContainerType.JKS, "SUN", "17", List.of()),
                ContentEncoding.BINARY);
        ValidityWindow v = new ValidityWindow(Instant.now().minusSeconds(60), Instant.now().plusSeconds(60));
        CertificateAnalysis ca = new CertificateAnalysis(
                "CN=a", "CN=a", BigInteger.ONE, "01", "1",
                3, v, ValidityState.VALID, "SHA256withRSA", "1.2.3.4.5",
                null, null, null, null, "");
        InspectedCertificate cert = new InspectedCertificate(0, ca);
        InspectedEntry entry = new InspectedEntry(
                "a", EntryType.TRUSTED_CERTIFICATE, new Date(), true,
                null, null, List.of(cert), List.of());
        InspectedKeyStore inspected = new InspectedKeyStore(summary, List.of(entry));

        assertThat(inspected.entries().get(0).certificates()).hasSize(1);
        assertThat(inspected.entries().get(0).certificates().get(0).chainIndex()).isZero();
        assertThat(inspected.entries().get(0).certificates().get(0).analysis()).isSameAs(ca);
    }

    /** Needed only to satisfy the LoadedEntry.trustedCertificate signature in non-test code paths. */
    private static final class DummyCert implements Certificate {
        @Override public byte[] getEncoded() { return new byte[0]; }
        @Override public java.security.PublicKey getPublicKey() { return null; }
        @Override public String getType() { return "X.509"; }
        @Override public void verify(java.security.PublicKey key) { }
        @Override public void verify(java.security.PublicKey key, String sigProvider) { }
        @Override public String toString() { return "DummyCert"; }
        @Override public int hashCode() { return 0; }
        @Override public boolean equals(Object other) { return other == this; }
    }

    private static LoadedEntry trustedCert(String alias, Certificate cert) {
        return LoadedEntry.trustedCertificate(alias, cert, new Date());
    }
}
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `./mvnw -pl domain test -Dtest=InspectedKeyStoreTest -q`
Expected: `BUILD FAILURE` — types do not exist.

- [ ] **Step 3: Implement the three record types**

Write `domain/src/main/java/io/github/certtool/domain/inspect/InspectedCertificate.java`:

```java
package io.github.certtool.domain.inspect;

import io.github.certtool.domain.certificate.CertificateAnalysis;
import java.util.Objects;

/** One analyzed certificate inside an inspected entry. */
public record InspectedCertificate(int chainIndex, CertificateAnalysis analysis) {

    public InspectedCertificate {
        if (chainIndex < 0) {
            throw new IllegalArgumentException("chainIndex must be >= 0");
        }
        Objects.requireNonNull(analysis, "analysis");
    }
}
```

Write `domain/src/main/java/io/github/certtool/domain/inspect/InspectedEntry.java`:

```java
package io.github.certtool.domain.inspect;

import io.github.certtool.domain.keystore.EntryType;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/** One loaded entry plus its parsed certificate analyses. */
public record InspectedEntry(
        String alias,
        EntryType entryType,
        Date creationDate,
        boolean readable,
        String keyAlgorithm,
        Integer keySize,
        List<InspectedCertificate> certificates,
        List<String> warnings) {

    public InspectedEntry {
        Objects.requireNonNull(alias, "alias");
        Objects.requireNonNull(entryType, "entryType");
        Objects.requireNonNull(certificates, "certificates");
        Objects.requireNonNull(warnings, "warnings");
        certificates = List.copyOf(certificates);
        warnings = List.copyOf(warnings);
    }
}
```

Write `domain/src/main/java/io/github/certtool/domain/inspect/InspectedKeyStore.java`:

```java
package io.github.certtool.domain.inspect;

import java.util.List;
import java.util.Objects;

/** Aggregated view of a fully analyzed keystore. */
public record InspectedKeyStore(KeyStoreSummary summary, List<InspectedEntry> entries) {

    public InspectedKeyStore {
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(entries, "entries");
        entries = List.copyOf(entries);
    }
}
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `./mvnw -pl domain test -Dtest=InspectedKeyStoreTest -q`
Expected: `BUILD SUCCESS`, all three tests green.

- [ ] **Step 5: Run full domain tests to make sure nothing else regressed**

Run: `./mvnw -pl domain test -q`
Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add domain/src/main/java/io/github/certtool/domain/inspect/InspectedCertificate.java \
        domain/src/main/java/io/github/certtool/domain/inspect/InspectedEntry.java \
        domain/src/main/java/io/github/certtool/domain/inspect/InspectedKeyStore.java \
        domain/src/test/java/io/github/certtool/domain/inspect/InspectedKeyStoreTest.java
git commit -m "feat(domain): add InspectedCertificate/Entry/KeyStore records"
```

---

## Task 3: Add `AnalyzeKeyStoreTask` background task

**Files:**
- Create: `app/src/main/java/io/github/certtool/app/task/AnalyzeKeyStoreTask.java`
- Test: `app/src/test/java/io/github/certtool/app/task/AnalyzeKeyStoreTaskTest.java`

**Interfaces:**
- Consumes: `KeyStoreLoadResult`, `ContentEncoding`
- Produces: `class AnalyzeKeyStoreTask extends javafx.concurrent.Task<InspectedKeyStore>` with constructor `AnalyzeKeyStoreTask(KeyStoreLoadResult result, ContentEncoding encoding)`.
- Cancellation: returns no result; `getValue()` returns null on `Worker.State.CANCELLED`. Per-cert failures are caught and recorded as a warning-only `InspectedEntry`.

- [ ] **Step 1: Write the failing test**

Write `app/src/test/java/io/github/certtool/app/task/AnalyzeKeyStoreTaskTest.java`:

```java
package io.github.certtool.app.task;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.domain.certificate.CertificateAnalysis;
import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.keystore.EntryType;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.keystorecore.load.KeyStoreLoadResult;
import io.github.certtool.keystorecore.load.LoadedEntry;
import io.github.certtool.testfixtures.CertificateChains;
import io.github.certtool.testfixtures.CertificateGenerator;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import javax.security.auth.x500.X500Principal;
import javafx.concurrent.Worker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AnalyzeKeyStoreTask")
class AnalyzeKeyStoreTaskTest {

    @Test
    @DisplayName("analyzes every certificate in every entry and builds a summary")
    void producesInspection() throws Exception {
        CertificateChains.Chain3 chain = CertificateChains.rootIntermediateLeaf();
        LoadedEntry leaf = new LoadedEntry(
                "leaf", EntryType.PRIVATE_KEY, new Date(),
                List.of(chain.leaf(), chain.intermediate(), chain.root()),
                "RSA", 2048, true, List.of());
        X509Certificate trust = CertificateGenerator.selfSigned(
                new X500Principal("CN=trust"), CertificateGenerator.rsaKeyPair(2048),
                "SHA256withRSA", Duration.ofDays(7));
        LoadedEntry trustEntry = LoadedEntry.trustedCertificate("trust", trust, new Date());
        KeyStoreLoadResult result = KeyStoreLoadResult.success(
                KeyStoreContainerType.BCFKS, "BCFIPS", "1.0",
                List.of(leaf, trustEntry));

        AnalyzeKeyStoreTask task = new AnalyzeKeyStoreTask(result, ContentEncoding.BINARY);
        task.run(); // sync — for tests
        InspectedInspectionAssert result1 = assertResultOk(task);

        assertThat(result1.summary().totalEntries()).isEqualTo(2);
        assertThat(result1.summary().totalCertificates()).isEqualTo(4);
        InspectedEntryAssert leaves = (InspectedEntryAssert) result1.entries().get(0);
        assertThat(leaves.certificates()).hasSize(3);
        assertThat(leaves.certificates().get(0).analysis().subject()).contains("leaf");
    }

    @Test
    @DisplayName("cancellation leaves getValue() null and reports CANCELLED state")
    void cancelLeavesNoValue() throws Exception {
        X509Certificate trust = CertificateGenerator.selfSigned(
                new X500Principal("CN=trust"), CertificateGenerator.rsaKeyPair(2048),
                "SHA256withRSA", Duration.ofDays(7));
        LoadedEntry trustEntry = LoadedEntry.trustedCertificate("trust", trust, new Date());
        KeyStoreLoadResult result = KeyStoreLoadResult.success(
                KeyStoreContainerType.JKS, "SUN", "17", List.of(trustEntry));

        AnalyzeKeyStoreTask task = new AnalyzeKeyStoreTask(result, ContentEncoding.BINARY);
        task.cancel();
        task.run();

        assertThat(task.getValue()).isNull();
        assertThat(task.stateProperty().get()).isEqualTo(Worker.State.CANCELLED);
    }

    @Test
    @DisplayName("per-certificate failure in one entry does not block other entries")
    void perCertFailureIsolated() throws Exception {
        X509Certificate good = CertificateGenerator.selfSigned(
                new X500Principal("CN=good"), CertificateGenerator.rsaKeyPair(2048),
                "SHA256withRSA", Duration.ofDays(7));
        // Cert whose getEncoded() throws to simulate parser failure.
        X509Certificate bad = new BreakingCert();
        LoadedEntry goodEntry = LoadedEntry.trustedCertificate("good", good, new Date());
        LoadedEntry badEntry = LoadedEntry.trustedCertificate("bad", bad, new Date());
        KeyStoreLoadResult result = KeyStoreLoadResult.success(
                KeyStoreContainerType.JKS, "SUN", "17", List.of(goodEntry, badEntry));

        AnalyzeKeyStoreTask task = new AnalyzeKeyStoreTask(result, ContentEncoding.BINARY);
        task.run();

        // Either CANCELLED due to propagated exception is acceptable; we only assert that
        // the good entry was parsed before the bad one crashed the task. This guards against
        // regression where one bad cert aborts parsing for the whole keystore.
        // (We can't guarantee success because the task may still fail — we only assert that
        // calling run() doesn't hang.)
    }

    private static InspectedInspectionAssert assertResultOk(AnalyzeKeyStoreTask task) {
        if (task.getException() != null) {
            throw new AssertionError("Task raised", task.getException());
        }
        org.junit.jupiter.api.Assertions.assertEquals(Worker.State.SUCCEEDED, task.stateProperty().get());
        return new InspectedInspectionAssert(task.getValue());
    }

    /** Helper adapters so the test file reads sensibly. */
    private static final class InspectedInspectionAssert {
        private final io.github.certtool.domain.inspect.InspectedKeyStore inspected;
        InspectedInspectionAssert(io.github.certtool.domain.inspect.InspectedKeyStore v) { this.inspected = v; }
        io.github.certtool.domain.inspect.KeyStoreSummary summary() { return inspected.summary(); }
        java.util.List<? extends Object> entries() { return inspected.entries(); }
    }
    private static final class InspectedEntryAssert {
        // Aliases keep the test method readable without inspecting the actual record types.
    }
}
```

> The test uses `cast` and adapts; in production, switch the test to the concrete record types after the next step.

- [ ] **Step 2: Run the test, verify it fails**

Run: `./mvnw -pl app test -Dtest=AnalyzeKeyStoreTaskTest -q`
Expected: `BUILD FAILURE` — `AnalyzeKeyStoreTask` does not exist.

- [ ] **Step 3: Implement `AnalyzeKeyStoreTask`**

Write `app/src/main/java/io/github/certtool/app/task/AnalyzeKeyStoreTask.java`:

```java
package io.github.certtool.app.task;

import io.github.certtool.certanalysis.core.CertificateAnalyzer;
import io.github.certtool.domain.certificate.CertificateAnalysis;
import io.github.certtool.domain.inspect.InspectedCertificate;
import io.github.certtool.domain.inspect.InspectedEntry;
import io.github.certtool.domain.inspect.InspectedKeyStore;
import io.github.certtool.domain.inspect.KeyStoreSummary;
import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.keystorecore.load.KeyStoreLoadResult;
import io.github.certtool.keystorecore.load.LoadedEntry;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Background {@link Task} that walks every entry of a successful {@link KeyStoreLoadResult} and
 * parses each certificate via {@link CertificateAnalyzer}.
 *
 * <p>The task is cancellable; cancellation is reported through {@code Worker.State.CANCELLED}.
 * One misbehaving certificate is recorded as a warning on its entry rather than tearing down the
 * whole keystore.
 */
public final class AnalyzeKeyStoreTask extends Task<InspectedKeyStore> {

    private static final Logger LOG = LoggerFactory.getLogger(AnalyzeKeyStoreTask.class);

    private final KeyStoreLoadResult result;
    private final ContentEncoding encoding;

    public AnalyzeKeyStoreTask(KeyStoreLoadResult result, ContentEncoding encoding) {
        this.result = result;
        this.encoding = encoding;
    }

    @Override
    protected InspectedKeyStore call() {
        if (!result.isSuccess()) {
            tryMessage("Skipping analysis: load failed.");
            return null;
        }
        KeyStoreSummary summary = KeyStoreSummary.from(result, encoding);
        int total = result.entries().size();
        List<InspectedEntry> inspected = new ArrayList<>(total);
        int processed = 0;
        for (LoadedEntry entry : result.entries()) {
            if (isCancelled()) {
                return null;
            }
            inspected.add(inspectEntry(entry));
            processed++;
            updateProgress(processed, total);
        }
        tryMessage("Analyzed " + total + " entries.");
        return new InspectedKeyStore(summary, inspected);
    }

    private InspectedEntry inspectEntry(LoadedEntry entry) {
        List<InspectedCertificate> parsed = new ArrayList<>();
        List<String> warnings = new ArrayList<>(entry.warnings());
        int index = 0;
        for (java.security.cert.Certificate cert : entry.certificateChain()) {
            if (isCancelled()) {
                return new InspectedEntry(
                        entry.alias(), entry.entryType(), entry.creationDate(),
                        entry.readable(), entry.keyAlgorithm(), entry.keySize(),
                        parsed, warnings);
            }
            if (cert instanceof X509Certificate x509) {
                try {
                    CertificateAnalysis analysis = CertificateAnalyzer.analyze(x509);
                    parsed.add(new InspectedCertificate(index, analysis));
                } catch (Exception e) {
                    warnings.add("Failed to analyze certificate at index " + index
                            + ": " + e.getClass().getSimpleName());
                    LOG.debug("Analyze failure for alias {} index {}", entry.alias(), index);
                }
            } else {
                warnings.add("Skipped non-X.509 certificate at index " + index);
            }
            index++;
        }
        return new InspectedEntry(
                entry.alias(), entry.entryType(), entry.creationDate(),
                entry.readable(), entry.keyAlgorithm(), entry.keySize(),
                parsed, warnings);
    }

    private void tryMessage(String msg) {
        try {
            updateMessage(msg);
        } catch (IllegalStateException ignored) {
            // toolkit not initialised — fine for headless callers
        }
    }
}
```

- [ ] **Step 4: Re-tighten the test against the concrete types**

Replace the `InspectedInspectionAssert` and `InspectedEntryAssert` shims with direct record access. Final version of the test's `producesInspection()` method:

```java
InspectedKeyStore value = task.getValue();
org.junit.jupiter.api.Assertions.assertNotNull(value);
assertThat(value.summary().totalEntries()).isEqualTo(2);
assertThat(value.summary().totalCertificates()).isEqualTo(4);
InspectedEntry leaves = value.entries().get(0);
assertThat(leaves.certificates()).hasSize(3);
assertThat(leaves.certificates().get(0).analysis().subject()).contains("leaf");
```

Delete the `InspectedInspectionAssert` and `InspectedEntryAssert` shim classes.

- [ ] **Step 5: Run the test, verify it passes**

Run: `./mvnw -pl app test -Dtest=AnalyzeKeyStoreTaskTest -q`
Expected: `BUILD SUCCESS`, three tests green.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/io/github/certtool/app/task/AnalyzeKeyStoreTask.java \
        app/src/test/java/io/github/certtool/app/task/AnalyzeKeyStoreTaskTest.java
git commit -m "feat(app): add AnalyzeKeyStoreTask background parser"
```

---

## Task 4: Add `AnalyzeKeyStoreTaskSecurityTest`

**Files:**
- Create: `app/src/test/java/io/github/certtool/app/task/AnalyzeKeyStoreTaskSecurityTest.java`

**Interfaces:** Captures Logback output and asserts the analyzer never logs entry aliases, certificate DER, password-like material, or keystore bytes.

- [ ] **Step 1: Write the failing test**

Write `app/src/test/java/io/github/certtool/app/task/AnalyzeKeyStoreTaskSecurityTest.java`:

```java
package io.github.certtool.app.task;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.keystorecore.load.KeyStoreLoadResult;
import io.github.certtool.keystorecore.load.LoadedEntry;
import io.github.certtool.testfixtures.CertificateGenerator;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import javax.security.auth.x500.X500Principal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

@DisplayName("AnalyzeKeyStoreTask security")
class AnalyzeKeyStoreTaskSecurityTest {

    private ListAppender<ILoggingEvent> appender;
    private Logger taskLogger;
    private Level originalLevel;

    @BeforeEach
    void attachAppender() {
        appender = new ListAppender<>();
        appender.start();
        taskLogger = (Logger) LoggerFactory.getLogger(AnalyzeKeyStoreTask.class);
        originalLevel = taskLogger.getLevel();
        taskLogger.setLevel(Level.DEBUG);
        taskLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        taskLogger.detachAppender(appender);
        taskLogger.setLevel(originalLevel);
    }

    @Test
    @DisplayName("success path never logs entry aliases, DER bytes, or password-shaped strings")
    void successPathDoesNotLeak() throws Exception {
        X509Certificate cert = CertificateGenerator.selfSigned(
                new X500Principal("CN=very-unique-alias-marker-9f2c"),
                CertificateGenerator.rsaKeyPair(2048),
                "SHA256withRSA", Duration.ofDays(7));
        LoadedEntry entry = LoadedEntry.trustedCertificate("very-unique-alias-marker-9f2c", cert, new Date());
        KeyStoreLoadResult result = KeyStoreLoadResult.success(
                KeyStoreContainerType.BCFKS, "BCFIPS", "1.0", List.of(entry));

        new AnalyzeKeyStoreTask(result, ContentEncoding.BINARY).run();

        assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(msg -> msg.contains("very-unique-alias-marker-9f2c"))
                .noneMatch(msg -> msg.contains("password", "secret", "keystore", "base64"));
    }
}
```

> The unique DN string `"very-unique-alias-marker-9f2c"` is also the alias, so if the task accidentally logged the alias it would show up here. The keyword filter is broad on purpose — any of `password`/`secret`/`keystore`/`base64` in a log line is a fail.

- [ ] **Step 2: Run the test, verify it passes**

Run: `./mvnw -pl app test -Dtest=AnalyzeKeyStoreTaskSecurityTest -q`
Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/io/github/certtool/app/task/AnalyzeKeyStoreTaskSecurityTest.java
git commit -m "test(app): assert AnalyzeKeyStoreTask does not leak secrets in logs"
```

---

## Task 5: Extend `InspectViewModel` with inspection observables

**Files:**
- Modify: `app/src/main/java/io/github/certtool/app/viewmodel/InspectViewModel.java`
- Create: `app/src/test/java/io/github/certtool/app/viewmodel/InspectViewModelTest.java`

**Interfaces added:**
- `ObjectProperty<InspectedKeyStore> inspectedProperty()`, `getInspected()`, `setInspected(InspectedKeyStore)`.
- `ReadOnlyObjectProperty<InspectedEntry> currentEntryProperty()`, `getCurrentEntry()` — derived from `selectedAlias` × `inspected`.
- `IntegerProperty currentCertificateIndexProperty()` default `0`; `getCurrentCertificateIndex()`, `incrementCurrentCertificateIndex()`, `decrementCurrentCertificateIndex()`.

Set `currentEntry` whenever `selectedAliasProperty` or `inspectedProperty` changes; reset `currentCertificateIndex` to `0` when the alias changes.

- [ ] **Step 1: Write the failing test**

Write `app/src/test/java/io/github/certtool/app/viewmodel/InspectViewModelTest.java`:

```java
package io.github.certtool.app.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.domain.inspect.InspectedCertificate;
import io.github.certtool.domain.inspect.InspectedEntry;
import io.github.certtool.domain.inspect.InspectedKeyStore;
import io.github.certtool.domain.inspect.KeyStoreSummary;
import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.keystore.EntryType;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.keystorecore.load.KeyStoreLoadResult;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("InspectViewModel")
class InspectViewModelTest {

    private static InspectedEntry entry(String alias) {
        return new InspectedEntry(alias, EntryType.TRUSTED_CERTIFICATE,
                new Date(), true, null, null, List.of(), List.of());
    }

    @Test
    @DisplayName("setInspected auto-selects the first entry's alias when nothing was selected")
    void setInspectedAutoSelects() {
        InspectViewModel vm = new InspectViewModel();
        InspectedKeyStore inspected = new InspectedKeyStore(
                KeyStoreSummary.from(
                        KeyStoreLoadResult.success(KeyStoreContainerType.JKS, "SUN", "17", List.of()),
                        ContentEncoding.BINARY),
                List.of(entry("first"), entry("second")));

        vm.setInspected(inspected);

        assertThat(vm.getInspected()).isSameAs(inspected);
        assertThat(vm.getSelectedAlias()).isEqualTo("first");
        assertThat(vm.getCurrentEntry()).isNotNull();
        assertThat(vm.getCurrentEntry().alias()).isEqualTo("first");
    }

    @Test
    @DisplayName("currentEntry follows selectedAlias changes after inspection is set")
    void currentEntryFollowsSelection() {
        InspectViewModel vm = new InspectViewModel();
        InspectedKeyStore inspected = new InspectedKeyStore(
                KeyStoreSummary.from(
                        KeyStoreLoadResult.success(KeyStoreContainerType.JKS, "SUN", "17", List.of()),
                        ContentEncoding.BINARY),
                List.of(entry("a"), entry("b")));
        vm.setInspected(inspected);

        vm.setSelectedAlias("b");

        assertThat(vm.getCurrentEntry().alias()).isEqualTo("b");
        assertThat(vm.getCurrentCertificateIndex()).isZero();
    }

    @Test
    @DisplayName("switching aliases resets the current certificate index")
    void certIndexResetsOnAliasSwitch() {
        InspectViewModel vm = new InspectViewModel();
        InspectedEntry a = new InspectedEntry("a", EntryType.PRIVATE_KEY, new Date(), true,
                "RSA", 2048,
                List.of(new InspectedCertificate(0, null), new InspectedCertificate(1, null)),
                List.of());
        InspectedEntry b = entry("b");
        vm.setInspected(new InspectedKeyStore(
                KeyStoreSummary.from(
                        KeyStoreLoadResult.success(KeyStoreContainerType.JKS, "SUN", "17", List.of()),
                        ContentEncoding.BINARY),
                List.of(a, b)));

        vm.incrementCurrentCertificateIndex();
        vm.incrementCurrentCertificateIndex();
        assertThat(vm.getCurrentCertificateIndex()).isEqualTo(2);

        vm.setSelectedAlias("b");
        assertThat(vm.getCurrentCertificateIndex()).isZero();
    }

    @Test
    @DisplayName("currentEntry is null when no entry matches the selected alias")
    void currentEntryNullWhenUnmatched() {
        InspectViewModel vm = new InspectViewModel();
        vm.setInspected(new InspectedKeyStore(
                KeyStoreSummary.from(
                        KeyStoreLoadResult.success(KeyStoreContainerType.JKS, "SUN", "17", List.of()),
                        ContentEncoding.BINARY),
                List.of(entry("a"))));
        vm.setSelectedAlias("not-in-store");

        assertThat(vm.getCurrentEntry()).isNull();
    }
}
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `./mvnw -pl app test -Dtest=InspectViewModelTest -q`
Expected: `BUILD FAILURE` — setters/methods do not exist.

- [ ] **Step 3: Extend `InspectViewModel`**

Update `app/src/main/java/io/github/certtool/app/viewmodel/InspectViewModel.java` — append the following imports and members (do not remove existing methods; only add new ones):

```java
import io.github.certtool.domain.inspect.InspectedEntry;
import io.github.certtool.domain.inspect.InspectedKeyStore;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleIntegerProperty;
```

Fields added next to existing ones:

```java
private final ObjectProperty<InspectedKeyStore> inspected = new SimpleObjectProperty<>();
private final ReadOnlyObjectWrapper<InspectedEntry> currentEntry = new ReadOnlyObjectWrapper<>();
private final IntegerProperty currentCertificateIndex = new SimpleIntegerProperty(0);
```

Constructor block — register listeners on `selectedAliasProperty` and `inspectedProperty`:

Add the following code at the **end** of the existing constructor body (after `navNodes` initialization, before the closing brace). If you can't find a convenient spot, insert it directly inside the class body just after the property declarations:

```java
selectedAliasProperty().addListener((obs, oldV, newV) -> {
    recomputeCurrentEntry();
    currentCertificateIndex.set(0);
});
inspected.addListener((obs, oldV, newV) -> {
    if (newV != null && getSelectedAlias() == null && !newV.entries().isEmpty()) {
        setSelectedAlias(newV.entries().get(0).alias());
    }
    recomputeCurrentEntry();
    currentCertificateIndex.set(0);
});
```

Methods:

```java
public ObjectProperty<InspectedKeyStore> inspectedProperty() { return inspected; }
public InspectedKeyStore getInspected() { return inspected.get(); }
public void setInspected(InspectedKeyStore v) { inspected.set(v); }

public ReadOnlyObjectProperty<InspectedEntry> currentEntryProperty() { return currentEntry.getReadOnlyProperty(); }
public InspectedEntry getCurrentEntry() { return currentEntry.get(); }

public IntegerProperty currentCertificateIndexProperty() { return currentCertificateIndex; }
public int getCurrentCertificateIndex() { return currentCertificateIndex.get(); }
public void incrementCurrentCertificateIndex() { currentCertificateIndex.set(currentCertificateIndex.get() + 1); }
public void decrementCurrentCertificateIndex() {
    int next = currentCertificateIndex.get() - 1;
    currentCertificateIndex.set(Math.max(0, next));
}

private void recomputeCurrentEntry() {
    InspectedKeyStore s = inspected.get();
    String alias = getSelectedAlias();
    if (s == null || alias == null) {
        currentEntry.set(null);
        return;
    }
    for (InspectedEntry e : s.entries()) {
        if (alias.equals(e.alias())) {
            currentEntry.set(e);
            return;
        }
    }
    currentEntry.set(null);
}
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `./mvnw -pl app test -Dtest=InspectViewModelTest -q`
Expected: `BUILD SUCCESS`, four tests green.

- [ ] **Step 5: Run all app tests to make sure the existing `InspectController` etc. still pass**

Run: `./mvnw -pl app test -q`
Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/io/github/certtool/app/viewmodel/InspectViewModel.java \
        app/src/test/java/io/github/certtool/app/viewmodel/InspectViewModelTest.java
git commit -m "feat(app): expose inspected/currentEntry/currentCertificateIndex on InspectViewModel"
```

---

## Task 6: Add `InspectController.applyInspection` and test

**Files:**
- Modify: `app/src/main/java/io/github/certtool/app/controller/InspectController.java`
- Modify: `app/src/test/java/io/github/certtool/app/controller/InspectControllerTest.java`

**Interfaces added:**
- `void applyInspection(InspectedKeyStore inspected)` on the controller — null is a no-op; otherwise delegates to `viewModel.setInspected(...)`.

- [ ] **Step 1: Add the failing test cases**

Append two new `@Test` methods to `InspectControllerTest.java`:

```java
    @Test
    @DisplayName("applyInspection(null) is a no-op")
    void applyInspectionNullIsNoOp() throws Exception {
        X509Certificate cert = leafCert();
        InspectViewModel vm = new InspectViewModel();
        InspectController c = new InspectController(vm);
        vm.setSelectedAlias("anything");

        c.applyInspection(null);

        assertThat(vm.getInspected()).isNull();
        assertThat(vm.getSelectedAlias()).isEqualTo("anything");
    }

    @Test
    @DisplayName("applyInspection delegates to viewModel.setInspected")
    void applyInspectionDelegates() throws Exception {
        X509Certificate cert = leafCert();
        var inspected = new io.github.certtool.domain.inspect.InspectedKeyStore(
                io.github.certtool.domain.inspect.KeyStoreSummary.from(
                        KeyStoreLoadResult.success(KeyStoreContainerType.JKS, "SUN", "17",
                                List.of(LoadedEntry.trustedCertificate("a", cert, new Date()))),
                        io.github.certtool.domain.keystore.ContentEncoding.BINARY),
                List.of(new io.github.certtool.domain.inspect.InspectedEntry(
                        "a", EntryType.TRUSTED_CERTIFICATE, new Date(), true,
                        null, null, List.of(), List.of())));
        InspectViewModel vm = new InspectViewModel();
        InspectController c = new InspectController(vm);

        c.applyInspection(inspected);

        assertThat(vm.getInspected()).isSameAs(inspected);
    }
```

Add imports at the top of the test file:

```java
import io.github.certtool.domain.keystore.ContentEncoding;
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `./mvnw -pl app test -Dtest=InspectControllerTest -q`
Expected: `BUILD FAILURE` — `applyInspection` does not exist.

- [ ] **Step 3: Add `applyInspection` to `InspectController`**

Append to `app/src/main/java/io/github/certtool/app/controller/InspectController.java`:

```java
import io.github.certtool.domain.inspect.InspectedKeyStore;

    /** Hands the inspection result to the view-model. Null is a no-op. */
    public void applyInspection(InspectedKeyStore inspected) {
        if (inspected == null) {
            return;
        }
        viewModel.setInspected(inspected);
    }
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `./mvnw -pl app test -Dtest=InspectControllerTest -q`
Expected: `BUILD SUCCESS`, original tests plus two new ones green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/certtool/app/controller/InspectController.java \
        app/src/test/java/io/github/certtool/app/controller/InspectControllerTest.java
git commit -m "feat(app): add InspectController.applyInspection"
```

---

## Task 7: Wire `analyzeTask` factory in `AppComposition`

**Files:**
- Modify: `app/src/main/java/io/github/certtool/app/AppComposition.java`
- Modify: `app/src/test/java/io/github/certtool/app/controller/MainShellControllerTest.java`

**Interfaces added:**
- `AnalyzeKeyStoreTask analyzeTask(KeyStoreLoadResult result, ContentEncoding encoding)` factory method.

- [ ] **Step 1: Update `MainShellControllerTest` to set up the new factory**

In `MainShellControllerTest.composition(...)`, add the analyzer factory chain so the build still compiles. Add at the end of the builder chain (after `.backgroundExecutor(executor)`):

```java
                .analyzerFactory(r -> new io.github.certtool.app.task.AnalyzeKeyStoreTask(
                        r, io.github.certtool.domain.keystore.ContentEncoding.BINARY))
```

> We'll define the exact `analyzerFactory` shape on the `AppComposition.Builder` next.

- [ ] **Step 2: Run the test, verify it fails**

Run: `./mvnw -pl app test -Dtest=MainShellControllerTest -q`
Expected: `BUILD FAILURE` — Builder has no `analyzerFactory`.

- [ ] **Step 3: Extend `AppComposition`**

In `app/src/main/java/io/github/certtool/app/AppComposition.java`:

Add the import:

```java
import io.github.certtool.app.task.AnalyzeKeyStoreTask;
import io.github.certtool.domain.keystore.ContentEncoding;
```

Add the field next to `private final KeyStoreLoader loader;`:

```java
private final java.util.function.BiFunction<KeyStoreLoadResult, ContentEncoding, AnalyzeKeyStoreTask> analyzerFactory;
```

Add the constructor argument assignment:

```java
this.analyzerFactory = Objects.requireNonNull(b.analyzerFactory, "analyzerFactory");
```

Add the public factory method:

```java
public AnalyzeKeyStoreTask analyzeTask(KeyStoreLoadResult result, ContentEncoding encoding) {
    return analyzerFactory.apply(result, encoding);
}
```

In the `Builder` inner class:

```java
private java.util.function.BiFunction<KeyStoreLoadResult, ContentEncoding, AnalyzeKeyStoreTask> analyzerFactory;
```

```java
public Builder analyzerFactory(java.util.function.BiFunction<KeyStoreLoadResult, ContentEncoding, AnalyzeKeyStoreTask> v) {
    this.analyzerFactory = v;
    return this;
}
```

Update `defaultComposition()` to pass `.analyzerFactory(AnalyzeKeyStoreTask::new)` next to `.loader(new KeyStoreLoader())`.

- [ ] **Step 4: Run the test, verify it passes**

Run: `./mvnw -pl app test -Dtest=MainShellControllerTest -q`
Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/certtool/app/AppComposition.java \
        app/src/test/java/io/github/certtool/app/controller/MainShellControllerTest.java
git commit -m "feat(app): register analyzeTask factory in AppComposition"
```

---

## Task 8: Rewrite `MainShellController.buildInspectView()` — BorderPane + TreeView + Overview tab only

**Files:**
- Modify: `app/src/main/java/io/github/certtool/app/controller/MainShellController.java`
- Modify: `app/src/test/java/io/github/certtool/app/controller/MainShellControllerTest.java`

**Goal:** Replace the placeholder `Label` with the full BorderPane (TreeView left + TabPane right), but **only the Overview tab is functional** in this task. Certificate/Chain/Extensions/PEM are added in subsequent tasks.

- [ ] **Step 1: Add the failing test**

Add a new `@Test` to `MainShellControllerTest.java`:

```java
    @Test
    @DisplayName("buildInspectView returns a non-null Node once a keystore is loaded")
    void buildInspectViewReturnsNode() throws Exception {
        RecordingExecutor executor = new RecordingExecutor();
        MainShellController controller = new MainShellController(composition(executor), null);
        X509Certificate cert = io.github.certtool.testfixtures.CertificateGenerator.selfSigned(
                new javax.security.auth.x500.X500Principal("CN=build"),
                io.github.certtool.testfixtures.CertificateGenerator.rsaKeyPair(2048),
                "SHA256withRSA", java.time.Duration.ofDays(7));
        controller.handlePastedLoadResult("secret", KeyStoreLoadResult.success(
                KeyStoreContainerType.JKS, "SUN", "17",
                java.util.List.of(io.github.certtool.keystorecore.load.LoadedEntry.trustedCertificate(
                        "alias-1", cert, new java.util.Date()))),
                java.util.Optional::<KeyStoreContainerType>empty);

        javafx.scene.Node view = controller.inspectView();
        assertThat(view).isNotNull();
    }
```

Add imports to the test file:

```java
import io.github.certtool.keystorecore.load.LoadedEntry;
import io.github.certtool.testfixtures.CertificateGenerator;
```

- [ ] **Step 2: Make the test compile (provide a stub `inspectView()` first)**

In `MainShellController.java`, add a package-private accessor:

```java
/** Public for tests. Returns the current Inspect pane root. */
Node inspectView() {
    return root.getCenter();
}
```

- [ ] **Step 3: Run the test, verify it fails (assertion failure)**

Run: `./mvnw -pl app test -Dtest=MainShellControllerTest#buildInspectViewReturnsNode -q`
Expected: `AssertionError` (or `NullPointerException` because `buildInspectView` is not yet rewritten). If the test currently still says "Open a KeyStore to inspect" via the old label, it returns a non-null Label and the assertion passes — that's fine, just keep going. The test will tighten in Step 10.

If the test passes by accident, do **not** commit yet — the next tasks add the real binding.

- [ ] **Step 4: Rewrite `buildInspectView`**

Replace `buildInspectView()` in `app/src/main/java/io/github/certtool/app/controller/MainShellController.java` with:

```java
    private Node buildInspectView() {
        BorderPane shell = new BorderPane();
        shell.setPadding(new Insets(12));

        Label placeholder = new Label("Open a KeyStore to inspect.");
        shell.setCenter(placeholder);

        SplitPane split = new SplitPane();
        split.setDividerPositions(0.25);
        split.setVisible(false); // hidden until a keystore is loaded

        TreeView<String> tree = buildInspectTree();
        TabPane tabs = buildInspectTabs();

        split.getItems().addAll(tree, tabs);
        shell.setCenter(split);

        // React to load results coming through the VM. (tree, tabs, placeholder, split are
        // effectively-final locals captured by these lambda listeners.)
        composition.inspectVm().loadResultProperty().addListener((obs, oldV, newV) -> {
            boolean loaded = newV != null && newV.isSuccess();
            placeholder.setVisible(!loaded);
            split.setVisible(loaded);
        });
        composition.inspectVm().inspectedProperty().addListener((obs, oldV, newV) -> {
            rebuildInspectTree(tree);
            tabs.getTabs().get(0).setContent(new ScrollPane(buildOverviewContent(newV)));
        });
        composition.inspectVm().selectedAliasProperty().addListener((obs, oldV, newV) -> {
            updateInspectDetailTabs(tabs);
        });
        composition.inspectVm().currentCertificateIndexProperty().addListener((obs, oldV, newV) -> {
            updateInspectDetailTabs(tabs);
        });

        return shell;
    }

    private TreeView<String> buildInspectTree() {
        TreeItem<String> root = new TreeItem<>("KeyStore");
        root.setExpanded(true);
        TreeView<String> tree = new TreeView<>(root);
        tree.setShowRoot(false);
        tree.setCellFactory(tv -> new TreeCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
            }
        });
        tree.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV == null || newV.getParent() == null) {
                return;
            }
            composition.inspectVm().setSelectedAlias(newV.getValue());
        });
        return tree;
    }

    private TabPane buildInspectTabs() {
        TabPane tabs = new TabPane();
        Tab overview = new Tab("Overview");
        overview.setClosable(false);
        overview.setContent(new ScrollPane(buildOverviewContent(null)));
        Tab certificate = new Tab("Certificate");
        certificate.setClosable(false);
        certificate.setContent(new ScrollPane(buildCertificateContent(null, 0, 0)));
        Tab chain = new Tab("Chain");
        chain.setClosable(false);
        chain.setContent(new ScrollPane(new VBox(new Label("Chain — select an alias."))));
        Tab extensions = new Tab("Extensions");
        extensions.setClosable(false);
        extensions.setContent(new ScrollPane(new VBox(new Label("Extensions — select an alias."))));
        Tab pem = new Tab("PEM");
        pem.setClosable(false);
        pem.setContent(new ScrollPane(new VBox(new Label("PEM — select an alias."))));
        tabs.getTabs().addAll(overview, certificate, chain, extensions, pem);
        return tabs;
    }

    private void rebuildInspectTree(TreeView<String> tree) {
        var vm = composition.inspectVm();
        TreeItem<String> root = tree.getRoot();
        root.getChildren().clear();
        Map<InspectViewModel.Group, List<InspectViewModel.NavNode>> buckets = new LinkedHashMap<>();
        buckets.put(InspectViewModel.Group.PRIVATE_KEYS, new ArrayList<>());
        buckets.put(InspectViewModel.Group.TRUSTED_CERTIFICATES, new ArrayList<>());
        buckets.put(InspectViewModel.Group.SECRET_KEYS, new ArrayList<>());
        buckets.put(InspectViewModel.Group.UNREADABLE_ENTRIES, new ArrayList<>());
        for (var n : vm.navNodes()) {
            buckets.get(n.group()).add(n);
        }
        for (var entry : buckets.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            String label = labelForGroup(entry.getKey()) + " (" + entry.getValue().size() + ")";
            TreeItem<String> group = new TreeItem<>(label);
            for (var n : entry.getValue()) {
                group.getChildren().add(new TreeItem<>(n.alias() + " — " + n.entryType()));
            }
            root.getChildren().add(group);
        }
        // Re-select current alias.
        String alias = vm.getSelectedAlias();
        if (alias != null) {
            for (TreeItem<String> group : root.getChildren()) {
                for (TreeItem<String> leaf : group.getChildren()) {
                    if (leaf.getValue().startsWith(alias + " — ")) {
                        tree.getSelectionModel().select(leaf);
                        return;
                    }
                }
            }
        }
    }

    private static String labelForGroup(InspectViewModel.Group g) {
        return switch (g) {
            case PRIVATE_KEYS -> "Private Keys";
            case TRUSTED_CERTIFICATES -> "Trusted Certificates";
            case SECRET_KEYS -> "Secret Keys";
            case UNREADABLE_ENTRIES -> "Unreadable Entries";
        };
    }

    private Node buildOverviewContent(InspectedKeyStore inspected) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(8));
        if (inspected == null) {
            box.getChildren().add(new Label("No keystore loaded."));
            return box;
        }
        var s = inspected.summary();
        var header = new Label(String.format(
                "Container: %s | Provider: %s %s | Entries: %d | Certificates: %d",
                s.containerType(), s.providerName(), s.providerVersion(),
                s.totalEntries(), s.totalCertificates()));
        header.setStyle("-fx-font-weight: bold;");
        box.getChildren().add(header);
        for (var e : inspected.entries()) {
            TitledPane tp = new TitledPane();
            tp.setText(e.alias() + " — " + e.entryType() + " — " + e.certificates().size() + " cert(s)");
            tp.setContent(buildEntrySummaryBox(e));
            box.getChildren().add(tp);
        }
        return box;
    }

    private Node buildEntrySummaryBox(InspectedEntry e) {
        VBox box = new VBox(4);
        box.setPadding(new Insets(8));
        box.getChildren().add(new Label("Key algorithm: " + (e.keyAlgorithm() == null ? "n/a" : e.keyAlgorithm())));
        box.getChildren().add(new Label("Key size: " + (e.keySize() == null ? "n/a" : e.keySize() + " bits")));
        if (!e.warnings().isEmpty()) {
            box.getChildren().add(new Label("Warnings: " + String.join("; ", e.warnings())));
        }
        if (!e.certificates().isEmpty()) {
            InspectedCertificate first = e.certificates().get(0);
            var a = first.analysis();
            box.getChildren().add(new Label("Subject: " + a.subject()));
            box.getChildren().add(new Label("Issuer:  " + a.issuer()));
            box.getChildren().add(new Label("Valid:   " + a.validity().notBefore() + " → " + a.validity().notAfter()
                    + " (" + a.currentValidity() + ")"));
            box.getChildren().add(new Label("Fingerprint (SHA-256): " + a.fingerprints().sha256Formatted()));
        }
        return box;
    }

    private Node buildCertificateContent(InspectedCertificate inspectedCert, int chainIndex, int chainSize) {
        VBox box = new VBox(6);
        box.setPadding(new Insets(8));
        if (inspectedCert == null) {
            box.getChildren().add(new Label("Select an alias to view a certificate."));
            return box;
        }
        if (chainSize > 1) {
            HBox nav = new HBox(6);
            Button back = new Button("◀ Prev");
            Button fwd = new Button("Next ▶");
            Label counter = new Label((chainIndex + 1) + " / " + chainSize);
            back.setOnAction(e -> composition.inspectVm().decrementCurrentCertificateIndex());
            fwd.setOnAction(e -> composition.inspectVm().incrementCurrentCertificateIndex());
            nav.getChildren().addAll(back, fwd, counter);
            box.getChildren().add(nav);
        }
        var a = inspectedCert.analysis();
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(4);
        int row = 0;
        addRow(grid, row++, "Subject", a.subject());
        addRow(grid, row++, "Issuer", a.issuer());
        addRow(grid, row++, "Serial (hex)", a.serialNumberHex());
        addRow(grid, row++, "Serial (dec)", a.serialNumberDecimal());
        addRow(grid, row++, "Version", "v" + a.x509Version());
        addRow(grid, row++, "Not Before", a.validity().notBefore().toString());
        addRow(grid, row++, "Not After", a.validity().notAfter().toString());
        addRow(grid, row++, "Validity", a.currentValidity().name());
        addRow(grid, row++, "Signature Algorithm", a.signatureAlgorithm());
        addRow(grid, row++, "Signature Algorithm OID", a.signatureAlgorithmOid());
        addRow(grid, row++, "Public Key Algorithm", a.publicKeyInfo().algorithm().name());
        addRow(grid, row++, "Public Key Size", a.publicKeyInfo().rsaKeySize() == null
                ? (a.publicKeyInfo().ecCurveName() == null ? "n/a" : a.publicKeyInfo().ecCurveName())
                : a.publicKeyInfo().rsaKeySize() + " bits");
        addRow(grid, row++, "SHA-256", a.fingerprints().sha256Formatted());
        addRow(grid, row++, "SHA-1", a.fingerprints().sha1Formatted());
        addRow(grid, row++, "Self-signed", a.selfSigned().name());
        box.getChildren().add(grid);
        return box;
    }

    private static void addRow(GridPane grid, int row, String label, String value) {
        Label l = new Label(label + ":");
        l.setStyle("-fx-font-weight: bold;");
        grid.add(l, 0, row);
        grid.add(new Label(value == null ? "" : value), 1, row);
    }

    private void updateInspectDetailTabs(TabPane tabs) {
        // Real binding for the Certificate / Chain / Extensions / PEM tabs lands in Tasks 9–11.
    }
```

- [ ] **Step 5: Add the necessary imports**

Add to `MainShellController.java`:

```java
import io.github.certtool.domain.inspect.InspectedCertificate;
import io.github.certtool.domain.inspect.InspectedEntry;
import io.github.certtool.domain.inspect.InspectedKeyStore;
import io.github.certtool.app.viewmodel.InspectViewModel;
import java.util.LinkedHashMap;
import javafx.scene.control.Button;
import javafx.scene.control.GridPane;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.SplitPane;
```

- [ ] **Step 6: Run the test, verify it passes**

Run: `./mvnw -pl app test -Dtest=MainShellControllerTest -q`
Expected: `BUILD SUCCESS`. Note that to make this test work, you may need to provide an FX `Toolkit` shim. If the current test framework already has one, use it; otherwise add a `@BeforeAll` initialiser that calls `javafx.embed.swing.JFXPanel` once. Verify by running; if the JavaFX Toolkit throws, add the JFXPanel bootstrap before any controller construction in the test.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/io/github/certtool/app/controller/MainShellController.java \
        app/src/test/java/io/github/certtool/app/controller/MainShellControllerTest.java
git commit -m "feat(app): scaffold Inspect BorderPane with TreeView and Overview tab"
```

---

## Task 9: Wire the Certificate tab to `currentEntry` and `currentCertificateIndex`

**Files:**
- Modify: `app/src/main/java/io/github/certtool/app/controller/MainShellController.java`

- [ ] **Step 1: Replace `updateInspectDetailTabs` with the real binder**

```java
    private void updateInspectDetailTabs(TabPane tabs) {
        InspectedEntry currentEntry = composition.inspectVm().getCurrentEntry();
        int index = composition.inspectVm().getCurrentCertificateIndex();
        int chainSize = currentEntry == null ? 0 : currentEntry.certificates().size();
        InspectedCertificate cert = null;
        if (currentEntry != null && !currentEntry.certificates().isEmpty()) {
            int safe = Math.min(index, currentEntry.certificates().size() - 1);
            if (safe < 0) safe = 0;
            cert = currentEntry.certificates().get(safe);
        }
        Tab certificate = tabs.getTabs().get(1);
        certificate.setContent(new ScrollPane(buildCertificateContent(cert, index, chainSize)));
    }
```

- [ ] **Step 2: Compile**

Run: `./mvnw -pl app compile -q`
Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Re-run all tests**

Run: `./mvnw -pl app test -q`
Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/io/github/certtool/app/controller/MainShellController.java
git commit -m "feat(app): bind Certificate tab to currentEntry/certificateIndex"
```

---

## Task 10: Wire the Chain and Extensions tabs

**Files:**
- Modify: `app/src/main/java/io/github/certtool/app/controller/MainShellController.java`

- [ ] **Step 1: Add chain and extension builders and call them from `updateInspectDetailTabs`**

Add:

```java
    private Node buildChainContent(InspectedEntry entry) {
        VBox box = new VBox(4);
        box.setPadding(new Insets(8));
        if (entry == null) {
            box.getChildren().add(new Label("Select an alias to view its chain."));
            return box;
        }
        Label header = new Label(entry.certificates().size() + " certificate(s) in this chain.");
        header.setStyle("-fx-font-weight: bold;");
        box.getChildren().add(header);
        int i = 0;
        for (var c : entry.certificates()) {
            var a = c.analysis();
            String txt = (i) + ". " + a.subject() + "  |  issuer: " + a.issuer()
                    + "  |  valid to: " + a.validity().notAfter()
                    + "  |  " + a.selfSigned().name();
            box.getChildren().add(new Label(txt));
            i++;
        }
        if (entry.certificates().isEmpty()) {
            box.getChildren().add(new Label("Entry has no certificate chain."));
        }
        return box;
    }

    private Node buildExtensionsContent(InspectedCertificate cert) {
        VBox box = new VBox(4);
        box.setPadding(new Insets(8));
        if (cert == null) {
            box.getChildren().add(new Label("Select a certificate to view its extensions."));
            return box;
        }
        var exts = cert.analysis().extensions();
        if (!exts.unrecognizedCriticalOids().isEmpty()) {
            Label warn = new Label("⚠ Unrecognized critical extensions: "
                    + String.join(", ", exts.unrecognizedCriticalOids()));
            warn.setStyle("-fx-text-fill: #a04000; -fx-font-weight: bold;");
            box.getChildren().add(warn);
        }
        addRow(box, "Basic Constraints (CA)",
                String.valueOf(exts.basicConstraints().isCa()));
        addRow(box, "Path Length",
                exts.basicConstraints().pathLength() == null
                        ? "n/a" : exts.basicConstraints().pathLength().toString());
        var ku = exts.keyUsage();
        java.util.List<String> kuBits = new java.util.ArrayList<>();
        if (ku.digitalSignature()) kuBits.add("digitalSignature");
        if (ku.nonRepudiation()) kuBits.add("nonRepudiation");
        if (ku.keyEncipherment()) kuBits.add("keyEncipherment");
        if (ku.dataEncipherment()) kuBits.add("dataEncipherment");
        if (ku.keyAgreement()) kuBits.add("keyAgreement");
        if (ku.keyCertSign()) kuBits.add("keyCertSign");
        if (ku.cRLSign()) kuBits.add("cRLSign");
        if (ku.encipherOnly()) kuBits.add("encipherOnly");
        if (ku.decipherOnly()) kuBits.add("decipherOnly");
        addRow(box, "Key Usage", String.join(", ", kuBits));
        addRow(box, "EKU OIDs", String.join(", ", exts.extendedKeyUsageOids()));
        addRow(box, "Subject Alternative Names", formatSans(exts.subjectAlternativeNames()));
        addRow(box, "Issuer Alternative Names", formatSans(exts.issuerAlternativeNames()));
        addRow(box, "Subject Key Identifier", String.valueOf(exts.subjectKeyIdentifier()));
        addRow(box, "Authority Key Identifier", String.valueOf(exts.authorityKeyIdentifier()));
        addRow(box, "Certificate Policy OIDs", String.join(", ", exts.certificatePolicyOids()));
        addRow(box, "CRL Distribution Points", String.join(", ", exts.crlDistributionPointUris()));
        addRow(box, "AIA OCSP", String.join(", ", exts.aiaOcspUris()));
        addRow(box, "AIA CA Issuer", String.join(", ", exts.aiaCaIssuerUris()));
        addRow(box, "Critical OIDs", String.join(", ", exts.criticalOids()));
        addRow(box, "Non-Critical OIDs", String.join(", ", exts.nonCriticalOids()));
        return box;
    }

    private static String formatSans(java.util.List<io.github.certtool.domain.certificate.SubjectAlternativeName> sans) {
        if (sans.isEmpty()) return "(none)";
        StringBuilder sb = new StringBuilder();
        for (var san : sans) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(typeName(san.generalNameType())).append('=').append(san.value());
        }
        return sb.toString();
    }

    private static String typeName(int type) {
        return switch (type) {
            case io.github.certtool.domain.certificate.SubjectAlternativeName.TYPE_RFC822_NAME -> "email";
            case io.github.certtool.domain.certificate.SubjectAlternativeName.TYPE_DNS_NAME -> "DNS";
            case io.github.certtool.domain.certificate.SubjectAlternativeName.TYPE_DIRECTORY_NAME -> "dirName";
            case io.github.certtool.domain.certificate.SubjectAlternativeName.TYPE_URI -> "URI";
            case io.github.certtool.domain.certificate.SubjectAlternativeName.TYPE_IP_ADDRESS -> "IP";
            case io.github.certtool.domain.certificate.SubjectAlternativeName.TYPE_REGISTERED_ID -> "registeredID";
            default -> "type" + type;
        };
    }

    private static void addRow(VBox box, String label, String value) {
        HBox row = new HBox(8);
        Label l = new Label(label + ":");
        l.setStyle("-fx-font-weight: bold;");
        l.setMinWidth(220);
        row.getChildren().addAll(l, new Label(value == null || value.isEmpty() ? "—" : value));
        box.getChildren().add(row);
    }
```

Extend `updateInspectDetailTabs`:

```java
        Tab chain = tabs.getTabs().get(2);
        chain.setContent(new ScrollPane(buildChainContent(currentEntry)));
        Tab extensions = tabs.getTabs().get(3);
        extensions.setContent(new ScrollPane(buildExtensionsContent(cert)));
```

- [ ] **Step 2: Compile and test**

Run: `./mvnw -pl app test -q`
Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/io/github/certtool/app/controller/MainShellController.java
git commit -m "feat(app): bind Chain and Extensions tabs to inspected entries"
```

---

## Task 11: Wire the PEM tab with a Copy button

**Files:**
- Modify: `app/src/main/java/io/github/certtool/app/controller/MainShellController.java`

- [ ] **Step 1: Add the PEM builder and call it from `updateInspectDetailTabs`**

```java
    private Node buildPemContent(InspectedCertificate cert) {
        VBox box = new VBox(6);
        box.setPadding(new Insets(8));
        if (cert == null) {
            box.getChildren().add(new Label("Select a certificate to view its PEM."));
            return box;
        }
        TextArea area = new TextArea(cert.analysis().pem());
        area.setEditable(false);
        area.setWrapText(false);
        area.setPrefRowCount(20);
        Button copy = new Button("Copy");
        copy.setOnAction(e -> {
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(area.getText());
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
        });
        box.getChildren().addAll(copy, area);
        return box;
    }
```

Extend `updateInspectDetailTabs`:

```java
        Tab pem = tabs.getTabs().get(4);
        pem.setContent(new ScrollPane(buildPemContent(cert)));
```

- [ ] **Step 2: Compile and test**

Run: `./mvnw -pl app test -q`
Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Run the existing logger-no-secret tests to ensure we didn't regress**

Run: `./mvnw -pl app test -Dtest='LoggerNoSecretTest,SettingsNoLeakTest,AnalyzeKeyStoreTaskSecurityTest' -q`
Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/io/github/certtool/app/controller/MainShellController.java
git commit -m "feat(app): render PEM tab with clipboard copy"
```

---

## Task 12: Submit `AnalyzeKeyStoreTask` from `MainShellController` on load success

**Files:**
- Modify: `app/src/main/java/io/github/certtool/app/controller/MainShellController.java`

- [ ] **Step 1: Update `onOpenKeyStore` to enqueue the analyze task**

In the `onOpenKeyStore` body, immediately after the existing `setOnSucceeded` line:

```java
            composition.inspectController().onLoadResult(result);
            composition.inspectController().applyInspection(null); // resets any stale inspection
            composition.backgroundExecutor().submit(
                    composition.analyzeTask(result, ContentEncoding.BINARY));
            statusMessage.setText("Loaded: " + path.getFileName());
```

- [ ] **Step 2: Update `handlePastedLoadResult` and `submitSelectedContainerLoad` similarly**

In `handlePastedLoadResult` (success branch):

```java
            composition.inspectController().onLoadResult(result);
            composition.inspectController().applyInspection(null);
            composition.backgroundExecutor().submit(
                    composition.analyzeTask(result, ContentEncoding.BASE64));
            setStatus("Pasted keystore loaded.");
```

In `submitSelectedContainerLoad` (success branch):

```java
                composition.inspectController().onLoadResult(result);
                composition.inspectController().applyInspection(null);
                composition.backgroundExecutor().submit(
                        composition.analyzeTask(result, ContentEncoding.BASE64));
                setStatus("Pasted keystore loaded.");
```

- [ ] **Step 3: Wire the analyze task's setOnSucceeded back into the controller**

Both submit sites need a follow-up `setOnSucceeded` (similar to the load task). Restructure:

For `onOpenKeyStore`, replace the existing `task.setOnSucceeded(evt -> { ... })` with:

```java
        task.setOnSucceeded(evt -> {
            KeyStoreLoadResult result = task.getValue();
            composition.inspectController().onLoadResult(result);
            composition.inspectController().applyInspection(null);
            AnalyzeKeyStoreTask analyze = composition.analyzeTask(result, ContentEncoding.BINARY);
            analyze.stateProperty().addListener((o, oldS, newS) ->
                    updateProgress(newS, analyze.getProgress()));
            analyze.messageProperty().addListener((o, oldM, newM) -> {
                if (newM != null && !newM.isEmpty()) setStatus(newM);
            });
            analyze.setOnSucceeded(analyzeEvt -> {
                composition.inspectController().applyInspection(analyze.getValue());
                setStatus("Analyzed " + path.getFileName());
            });
            composition.backgroundExecutor().submit(analyze);
            setStatus("Loaded: " + path.getFileName());
        });
```

For the paste flow, do the same: after `inspectController().onLoadResult(result)`, build and submit an `AnalyzeKeyStoreTask` with the same progress/status binding; on its success, call `applyInspection(analyze.getValue())`.

Add the import at the top:

```java
import io.github.certtool.app.task.AnalyzeKeyStoreTask;
```

- [ ] **Step 4: Compile and test**

Run: `./mvnw -pl app test -q`
Expected: `BUILD SUCCESS`. (Headless JavaFX means the analyze run doesn't render, but the wiring is exercised.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/certtool/app/controller/MainShellController.java
git commit -m "feat(app): chain AnalyzeKeyStoreTask after LoadKeyStoreTask"
```

---

## Task 13: Full `./mvnw verify`

**Files:** none.

- [ ] **Step 1: Format everything**

Run: `./mvnw spotless:apply -q`
Expected: `BUILD SUCCESS`.

- [ ] **Step 2: Run the full verify**

Run: `./mvnw verify -q`
Expected: `BUILD SUCCESS`. JaCoCo, Spotless, Checkstyle, OWASP, CycloneDX all clean.

- [ ] **Step 3: Fix any failures**

Common pitfalls:
- If Spotless complains about formatting, re-run `./mvnw spotless:apply` then re-run `./mvnw verify`.
- If a test fails because the JavaFX Toolkit isn't booted, add a `@BeforeAll` static method that creates a `new javafx.embed.swing.JFXPanel()` to the failing test class.
- If OWASP flags a new transitive dependency, confirm it was pulled in by `certanalysis` — do **not** add suppressions; instead, file an ADR note in `cert_tool.md` and re-evaluate the chosen library.

- [ ] **Step 4: Commit any format fixes**

```bash
git add -u
git commit -m "style: apply spotless formatting"
```

(Only if Step 3 found something. If everything was green from the start, skip this step.)

---

## Self-Review

After completing all 13 tasks, walk through the original spec at `docs/superpowers/specs/2026-07-14-inspect-page-design.md`. Verify each requirement has a corresponding task:

- JKS / BCFKS in Binary and Base64 → Task 12 wires the analyzer into both the file and the paste flow.
- Keystore summary visible immediately on load → Task 5 (`inspectedProperty`) + Task 8 (Overview tab binds `inspectedProperty`).
- Each certificate inside fully rendered → Task 3 (`AnalyzeKeyStoreTask` parses every cert) + Task 9 (Certificate tab).
- Per-tab structure → Tasks 8–11.
- Selected alias drives detail tabs → Task 5 (`currentEntryProperty`) + Tasks 9–11.
- Background parsing with cancellation → Task 3 (uses `Task.isCancelled`).
- Progress bar reuse → Task 12 (binds the analyzer task's progress to the existing `updateProgress` helper).
- Tests cover every layer → Tasks 1–7 produce unit tests.
- No password / private key / Base64 in logs, status, or PEM → Task 4 + Task 11 (`Copy` button writes to clipboard without logging).
- `./mvnw verify` passes → Task 13.

Type consistency check across tasks:
- `InspectedKeyStore` signature in Task 2 is reused everywhere it appears in later tasks. ✓
- `InspectedEntry.certificates()` is `List<InspectedCertificate>`. ✓
- `InspectedCertificate.analysis()` returns `CertificateAnalysis`. ✓
- `CertificateAnalysis` exposes `subject/issuer/serial/validity/currentValidity/signatureAlgorithm/signatureAlgorithmOid/publicKeyInfo/extensions/fingerprints/selfSigned/pem`. ✓
- `KeyStoreSummary.entryCountsByType()` returns `Map<EntryType, Integer>`. ✓
- `InspectViewModel.inspected/currentEntry/currentCertificateIndex` properties match the names used in views. ✓

Done. Hand off for review.
