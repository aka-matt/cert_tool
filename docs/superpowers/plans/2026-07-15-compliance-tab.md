# Compliance Tab Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the full Compliance tab (Profile picker, Run Assessment, Export Report, summary cards, filterable findings table, evidence/remediation detail, FIPS disclaimer) on top of the existing compliance engine, reporting module, and Inspect pipeline.

**Architecture:** Add a pure `RuleContextFactory` that maps `KeyStoreLoadResult` + `InspectedKeyStore` + `ContentEncoding` + `sourcePath` + live `RuntimeEnvironment` into a `RuleContext`. Extend `ComplianceViewModel` (selected finding, clear-on-new-keystore) and `InspectViewModel` (track `contentEncoding` + `sourcePath`). Replace the placeholder `buildComplianceView()` in `MainShellController` with the real layout and Run/Export handlers. Wire the existing File → Export Report menu to the same export path.

**Tech Stack:** Java 17 LTS, JavaFX 17 (`BorderPane`, `ComboBox`, `TableView`, `Label`, `Button`, `FileChooser`, `ExtensionFilter`, `ProgressBar`, `TextArea`, `SplitPane`), SLF4J/Logback, JUnit 5, AssertJ. Reuses existing `compliance-engine`, `reporting`, and `app/task/AssessmentTask` / `ExportReportTask`.

**Spec:** [`docs/superpowers/specs/2026-07-15-compliance-tab-design.md`](../specs/2026-07-15-compliance-tab-design.md)

## File Layout

**New**
- `app/src/main/java/io/github/certtool/app/compliance/RuleContextFactory.java`
- `app/src/test/java/io/github/certtool/app/compliance/RuleContextFactoryTest.java`

**Modified**
- `app/src/main/java/io/github/certtool/app/viewmodel/ComplianceViewModel.java` — `selectedFinding`, `clearReport()` helper
- `app/src/main/java/io/github/certtool/app/viewmodel/InspectViewModel.java` — `contentEncoding` + `sourcePath` properties
- `app/src/main/java/io/github/certtool/app/controller/ComplianceController.java` — `onKeyStoreChanged()` + `onFindingSelected()`
- `app/src/main/java/io/github/certtool/app/controller/MainShellController.java` — real `buildComplianceView()`; `complianceView()` accessor; Run/Export handlers; clear report on load; wire File → Export Report
- `app/src/test/java/io/github/certtool/app/controller/ComplianceControllerTest.java` — new cases
- `app/src/test/java/io/github/certtool/app/viewmodel/ComplianceViewModelTest.java` (new) — selected finding + clear
- `app/src/test/java/io/github/certtool/app/viewmodel/InspectViewModelTest.java` — encoding/sourcePath
- `app/src/test/java/io/github/certtool/app/controller/MainShellControllerTest.java` — `complianceView()` smoke + clear-on-load

**Unchanged**
- `compliance-engine/**`, `reporting/**`, all `domain/**`, `inspect/**`, `keystore-core/**`
- Inspect right pane (still the existing stub)
- Convert wizard and Runtime page (beyond shared menu export wiring)

## Global Constraints

- Java 17 LTS; no Java 21 features.
- Strict TDD: failing test → minimal impl → green → refactor → commit.
- Never block the JavaFX Application Thread. Assessment + export go through existing `Task` types on `composition.backgroundExecutor()`.
- No TestFX for this work. UI assertions use stable `Node.setId(...)` plus VM unit tests + the existing `./mvnw verify` pipeline.
- No log/status/dialog/report may contain store password, key password, private key, secret key, full Base64, or full keystore binary.
- UI copy and report title use **FIPS Compatibility Assessment** / **FIPS Readiness Assessment** wording only. Never "FIPS Certification" / "Official FIPS Validation" / "NIST Certified" / "正式认证结论".
- Always run `./mvnw spotless:apply` before `./mvnw verify` if any source was edited.
- Use existing `AppComposition` wiring; add no new external dependencies.

---

## Task 1: Extend `InspectViewModel` with `contentEncoding` and `sourcePath`

**Files:**
- Modify: `app/src/main/java/io/github/certtool/app/viewmodel/InspectViewModel.java`
- Test: `app/src/test/java/io/github/certtool/app/viewmodel/InspectViewModelTest.java`

**Interfaces:**
- Produces (on `InspectViewModel`):
  - `ObjectProperty<ContentEncoding> contentEncodingProperty()` (default `null`)
  - `ContentEncoding getContentEncoding()` / `void setContentEncoding(ContentEncoding e)`
  - `StringProperty sourcePathProperty()` (default `""`)
  - `String getSourcePath()` / `void setSourcePath(String p)`

- [ ] **Step 1: Write the failing test**

Append to `app/src/test/java/io/github/certtool/app/viewmodel/InspectViewModelTest.java` (or create the file if it does not exist with a `@DisplayName("InspectViewModel") class InspectViewModelTest` shell containing only this test):

```java
package io.github.certtool.app.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.domain.keystore.ContentEncoding;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("InspectViewModel source tracking")
class InspectViewModelSourceTest {

    @Test
    @DisplayName("contentEncoding and sourcePath default to null / empty and round-trip values")
    void sourceRoundTrip() {
        InspectViewModel vm = new InspectViewModel();

        assertThat(vm.getContentEncoding()).isNull();
        assertThat(vm.getSourcePath()).isEmpty();

        vm.setContentEncoding(ContentEncoding.BINARY);
        vm.setSourcePath("/tmp/store.jks");

        assertThat(vm.getContentEncoding()).isEqualTo(ContentEncoding.BINARY);
        assertThat(vm.getSourcePath()).isEqualTo("/tmp/store.jks");

        vm.setSourcePath(null);
        assertThat(vm.getSourcePath()).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -pl app test -Dtest=InspectViewModelSourceTest`
Expected: compile failure because `getContentEncoding()` / `setContentEncoding` / `getSourcePath()` / `setSourcePath` do not exist yet.

- [ ] **Step 3: Add the properties to `InspectViewModel`**

In `app/src/main/java/io/github/certtool/app/viewmodel/InspectViewModel.java`:

1. Add import `import io.github.certtool.domain.keystore.ContentEncoding;`.
2. Add fields with the other property fields:

```java
private final ObjectProperty<ContentEncoding> contentEncoding = new SimpleObjectProperty<>();
private final StringProperty sourcePath = new StringProperty("");
```

(`StringProperty` is abstract — use `SimpleStringProperty` in the field initializer:)

```java
private final StringProperty sourcePath = new SimpleStringProperty("");
```

3. Add accessors near the other property accessors:

```java
public ObjectProperty<ContentEncoding> contentEncodingProperty() { return contentEncoding; }
public ContentEncoding getContentEncoding() { return contentEncoding.get(); }
public void setContentEncoding(ContentEncoding e) { contentEncoding.set(e); }

public StringProperty sourcePathProperty() { return sourcePath; }
public String getSourcePath() {
    String v = sourcePath.get();
    return v == null ? "" : v;
}
public void setSourcePath(String p) { sourcePath.set(p == null ? "" : p); }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -pl app test -Dtest=InspectViewModelSourceTest`
Expected: PASS.

- [ ] **Step 5: Run app module tests to confirm no regressions**

Run: `./mvnw -pl app test`
Expected: PASS for all existing app tests.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/io/github/certtool/app/viewmodel/InspectViewModel.java \
        app/src/test/java/io/github/certtool/app/viewmodel/InspectViewModelSourceTest.java
git commit -m "feat(app): track contentEncoding and sourcePath on InspectViewModel"
```

---

## Task 2: Set `contentEncoding` + `sourcePath` from MainShell on successful load

**Files:**
- Modify: `app/src/main/java/io/github/certtool/app/controller/MainShellController.java`
  - Call sites: `onOpenKeyStore` (`task.setOnSucceeded` after `inspectController.onLoadResult`) and `handlePastedLoadResult` (the Base64 success branch).

**Interfaces:**
- Consumes: existing `Composition#inspectVm()` and the local `ContentEncoding` value already passed to `composition.analyzeTask(...)`.

- [ ] **Step 1: Add a tiny package-private helper to MainShellController**

Just below the existing `touch()` helper near the bottom of the file:

```java
/** Records encoding + source path on the Inspect view-model after a successful load. */
void recordLoadSource(ContentEncoding encoding, String sourcePath) {
    composition.inspectVm().setContentEncoding(encoding);
    composition.inspectVm().setSourcePath(sourcePath);
}
```

- [ ] **Step 2: Call helper from `onOpenKeyStore` after a successful load**

In `onOpenKeyStore`, after `composition.inspectController().onLoadResult(result);`, add:

```java
recordLoadSource(ContentEncoding.BINARY, selected.toString());
```

Use the `selected` local `java.io.File` already captured from the file chooser.

- [ ] **Step 3: Call helper from `handlePastedLoadResult`**

In `handlePastedLoadResult`, in the success branch (the `result != null && result.isSuccess()` block), after `composition.inspectController().onLoadResult(result);`, add:

```java
recordLoadSource(ContentEncoding.BASE64, null);
```

Also do the same in the `submitSelectedContainerLoad` path (search for `submitSelectedContainerLoad` in MainShellController) — after the inner `inspectController.onLoadResult` succeeds, call:

```java
recordLoadSource(ContentEncoding.BASE64, null);
```

(Inspect the existing method first; the helper is idempotent and safe to call after every successful load path.)

- [ ] **Step 4: Compile the app module**

Run: `./mvnw -pl app -am compile`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/certtool/app/controller/MainShellController.java
git commit -m "feat(app): record load source encoding and path on InspectViewModel"
```

---

## Task 3: Extend `ComplianceViewModel` with selected finding + clear helpers

**Files:**
- Modify: `app/src/main/java/io/github/certtool/app/viewmodel/ComplianceViewModel.java`
- Test: `app/src/test/java/io/github/certtool/app/viewmodel/ComplianceViewModelTest.java` (new)

**Interfaces:**
- Produces (on `ComplianceViewModel`):
  - `ObjectProperty<AssessmentFinding> selectedFindingProperty()`
  - `AssessmentFinding getSelectedFinding()` / `void setSelectedFinding(AssessmentFinding f)`
  - `void clearReport()` — sets report to null and selection to null; rebuilds filtered (no-op when already empty).

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/io/github/certtool/app/viewmodel/ComplianceViewModelTest.java`:

```java
package io.github.certtool.app.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.compliance.loader.DefaultProfiles;
import io.github.certtool.domain.assessment.AssessmentFinding;
import io.github.certtool.domain.assessment.AssessmentReport;
import io.github.certtool.domain.assessment.AssessmentStatus;
import io.github.certtool.domain.assessment.Severity;
import io.github.certtool.domain.profile.Profile;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ComplianceViewModel")
class ComplianceViewModelTest {

    private static AssessmentReport sampleReport() throws Exception {
        Profile p = DefaultProfiles.loadFips1403();
        return new AssessmentReport(p, Instant.parse("2026-07-15T00:00:00Z"), List.of(
                new AssessmentFinding("R-1", "Pass",
                        AssessmentStatus.PASS, Severity.INFO,
                        "ok", "ev", "rem", List.of()),
                new AssessmentFinding("R-2", "Warn",
                        AssessmentStatus.WARNING, Severity.MEDIUM,
                        "warn", "ev", "rem", List.of())));
    }

    @Test
    @DisplayName("selectedFinding property round-trips and starts null")
    void selectedFindingRoundTrip() throws Exception {
        ComplianceViewModel vm = new ComplianceViewModel();
        assertThat(vm.getSelectedFinding()).isNull();

        AssessmentReport r = sampleReport();
        vm.setReport(r);
        AssessmentFinding f = r.findings().get(0);
        vm.setSelectedFinding(f);

        assertThat(vm.getSelectedFinding()).isSameAs(f);
    }

    @Test
    @DisplayName("clearReport() resets report, filtered list, and selectedFinding")
    void clearReportResetsEverything() throws Exception {
        ComplianceViewModel vm = new ComplianceViewModel();
        vm.setReport(sampleReport());
        vm.setSelectedFinding(sampleReport().findings().get(0));

        vm.clearReport();

        assertThat(vm.getReport()).isNull();
        assertThat(vm.filteredFindings()).isEmpty();
        assertThat(vm.getSelectedFinding()).isNull();
        assertThat(vm.summaryCounts()).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -pl app test -Dtest=ComplianceViewModelTest`
Expected: compile failure because `getSelectedFinding()` / `setSelectedFinding(...)` / `clearReport()` do not exist.

- [ ] **Step 3: Add the property and helper to `ComplianceViewModel`**

In `app/src/main/java/io/github/certtool/app/viewmodel/ComplianceViewModel.java`:

1. Add field with the other properties:

```java
private final ObjectProperty<AssessmentFinding> selectedFinding = new SimpleObjectProperty<>();
```

2. Add accessors and the `clearReport()` helper:

```java
public ObjectProperty<AssessmentFinding> selectedFindingProperty() { return selectedFinding; }
public AssessmentFinding getSelectedFinding() { return selectedFinding.get(); }
public void setSelectedFinding(AssessmentFinding f) { selectedFinding.set(f); }

public void clearReport() {
    selectedFinding.set(null);
    report.set(null);
    rebuildFiltered();
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -pl app test -Dtest=ComplianceViewModelTest`
Expected: PASS.

- [ ] **Step 5: Confirm existing compliance controller tests still pass**

Run: `./mvnw -pl app test -Dtest=ComplianceControllerTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/io/github/certtool/app/viewmodel/ComplianceViewModel.java \
        app/src/test/java/io/github/certtool/app/viewmodel/ComplianceViewModelTest.java
git commit -m "feat(app): add selectedFinding and clearReport to ComplianceViewModel"
```

---

## Task 4: Extend `ComplianceController` with selection + clear-on-keystore handlers

**Files:**
- Modify: `app/src/main/java/io/github/certtool/app/controller/ComplianceController.java`
- Test: `app/src/test/java/io/github/certtool/app/controller/ComplianceControllerTest.java`

**Interfaces:**
- Produces (on `ComplianceController`):
  - `void onFindingSelected(AssessmentFinding finding)` → `viewModel.setSelectedFinding(finding)`
  - `void onKeyStoreChanged()` → `viewModel.clearReport()`

- [ ] **Step 1: Add failing tests**

Append to `app/src/test/java/io/github/certtool/app/controller/ComplianceControllerTest.java`:

```java
@Test
@DisplayName("onFindingSelected updates the view-model's selectedFinding")
void onFindingSelected() throws Exception {
    ComplianceViewModel vm = new ComplianceViewModel();
    ComplianceController c = new ComplianceController(vm);
    AssessmentReport r = sampleReport();
    c.onReportProduced(r);
    AssessmentFinding f = r.findings().get(1);

    c.onFindingSelected(f);

    assertThat(c.viewModel().getSelectedFinding()).isSameAs(f);
}

@Test
@DisplayName("onKeyStoreChanged clears the report and filtered findings")
void onKeyStoreChangedClears() throws Exception {
    ComplianceViewModel vm = new ComplianceViewModel();
    ComplianceController c = new ComplianceController(vm);
    c.onReportProduced(sampleReport());

    c.onKeyStoreChanged();

    assertThat(c.viewModel().getReport()).isNull();
    assertThat(c.viewModel().filteredFindings()).isEmpty();
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw -pl app test -Dtest=ComplianceControllerTest`
Expected: compile failure because `onFindingSelected` / `onKeyStoreChanged` do not exist.

- [ ] **Step 3: Add handlers to `ComplianceController`**

In `app/src/main/java/io/github/certtool/app/controller/ComplianceController.java`:

```java
public void onFindingSelected(AssessmentFinding finding) {
    viewModel.setSelectedFinding(finding);
}

public void onKeyStoreChanged() {
    viewModel.clearReport();
}
```

The existing `import io.github.certtool.domain.assessment.AssessmentFinding;` is already present.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw -pl app test -Dtest=ComplianceControllerTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/certtool/app/controller/ComplianceController.java \
        app/src/test/java/io/github/certtool/app/controller/ComplianceControllerTest.java
git commit -m "feat(app): expose finding selection and keystore-cleared handler on ComplianceController"
```

---

## Task 5: Add pure `RuleContextFactory`

**Files:**
- Create: `app/src/main/java/io/github/certtool/app/compliance/RuleContextFactory.java`
- Test: `app/src/test/java/io/github/certtool/app/compliance/RuleContextFactoryTest.java`

**Interfaces:**
- Produces:
  - `public static RuleContext from(KeyStoreLoadResult load, InspectedKeyStore inspected, ContentEncoding encoding, String sourcePathOrNull, long sizeBytes, RuntimeEnvironment runtime)`

Mapping rules (see spec §"RuleContext mapping"):
- Throws `IllegalArgumentException` when `load` is null / not success, `inspected` / `runtime` / `encoding` is null.
- `LoadedKeyStoreInfo` — `containerType` from `load.container()`; `encoding` as passed; `sourcePath` may be null; `sizeBytes` as passed (caller passes 0 when unknown); `integrityCheckPassed=true`; aliases ordered from `inspected.entries()` mapped by alias.
- Each `InspectedEntry` → `EntryAnalysis(alias, entryType, firstCertAnalysisOrNull, null, null)`. Use a helper that picks `inspectedEntry.certificates().get(0).analysis()` when the list is non-empty.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/io/github/certtool/app/compliance/RuleContextFactoryTest.java`:

```java
package io.github.certtool.app.compliance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.certtool.domain.certificate.CertificateAnalysis;
import io.github.certtool.domain.certificate.ExtensionAnalysis;
import io.github.certtool.domain.certificate.FingerprintBundle;
import io.github.certtool.domain.certificate.KeyAlgorithm;
import io.github.certtool.domain.certificate.PublicKeyInfo;
import io.github.certtool.domain.certificate.SelfSignedStatus;
import io.github.certtool.domain.certificate.ValidityState;
import io.github.certtool.domain.certificate.ValidityWindow;
import io.github.certtool.domain.context.EntryAnalysis;
import io.github.certtool.domain.context.LoadedKeyStoreInfo;
import io.github.certtool.domain.context.ProviderInfo;
import io.github.certtool.domain.context.RuntimeEnvironment;
import io.github.certtool.domain.inspect.InspectedCertificate;
import io.github.certtool.domain.inspect.InspectedEntry;
import io.github.certtool.domain.inspect.InspectedKeyStore;
import io.github.certtool.domain.inspect.KeyStoreSummary;
import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.keystore.EntryType;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.domain.load.KeyStoreLoadResult;
import io.github.certtool.domain.load.LoadedEntry;
import java.math.BigInteger;
import java.security.cert.Certificate;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RuleContextFactory")
class RuleContextFactoryTest {

    private static KeyStoreSummary summary(KeyStoreContainerType c) {
        return new KeyStoreSummary(c, ContentEncoding.BINARY, "SUN", "17",
                Map.of(EntryType.PRIVATE_KEY, 0,
                       EntryType.TRUSTED_CERTIFICATE, 1,
                       EntryType.SECRET_KEY, 0,
                       EntryType.UNKNOWN, 0),
                1, 1);
    }

    private static CertificateAnalysis leafAnalysis() {
        ValidityWindow w = new ValidityWindow(
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2027-01-01T00:00:00Z"));
        FingerprintBundle fp = new FingerprintBundle("aa", "bb", "AA", "BB");
        PublicKeyInfo pki = new PublicKeyInfo(KeyAlgorithm.RSA, 2048, null, null, null);
        ExtensionAnalysis ext = new ExtensionAnalysis(
                io.github.certtool.domain.certificate.BasicConstraintsInfo.absent(),
                io.github.certtool.domain.certificate.KeyUsageBits.empty(),
                List.of(), List.of(), List.of(),
                null, null,
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of());
        return new CertificateAnalysis(
                "CN=leaf", "CN=root", BigInteger.ONE, "01", "1",
                3, w, ValidityState.VALID, "SHA256withRSA", "1.2.840.113549.1.1.11",
                pki, ext, fp, new SelfSignedStatus(false, false),
                "-----BEGIN CERTIFICATE-----\n...\n-----END CERTIFICATE-----\n");
    }

    private static InspectedKeyStore inspected(String alias, EntryType type,
                                               CertificateAnalysis analysisOrNull) {
        List<InspectedCertificate> chain = analysisOrNull == null
                ? List.of()
                : List.of(new InspectedCertificate(0, analysisOrNull));
        InspectedEntry e = new InspectedEntry(alias, type, new Date(), true,
                type == EntryType.PRIVATE_KEY ? "RSA" : null,
                type == EntryType.PRIVATE_KEY ? 2048 : null,
                chain, List.of());
        return new InspectedKeyStore(summary(KeyStoreContainerType.JKS), List.of(e));
    }

    private static KeyStoreLoadResult load(List<LoadedEntry> entries) {
        return KeyStoreLoadResult.success(KeyStoreContainerType.JKS, "SUN", "17", entries);
    }

    private static RuntimeEnvironment runtime() {
        return new RuntimeEnvironment("Temurin", "17", "Linux", "amd64",
                List.of(new ProviderInfo("SUN", "17", "SUN", false, false)),
                Instant.parse("2026-07-15T00:00:00Z"));
    }

    @Test
    @DisplayName("maps a successful load + inspected keystore into a populated RuleContext")
    void mapsSuccessfulLoad() {
        CertificateAnalysis leaf = leafAnalysis();
        InspectedKeyStore ins = inspected("alias-1", EntryType.TRUSTED_CERTIFICATE, leaf);
        KeyStoreLoadResult res = load(List.of(
                LoadedEntry.trustedCertificate("alias-1", new DummyCert(), new Date())));

        var ctx = RuleContextFactory.from(res, ins, ContentEncoding.BINARY,
                "/tmp/a.jks", 1024L, runtime());

        LoadedKeyStoreInfo info = ctx.loadedKeyStore();
        assertThat(info.containerType()).isEqualTo(KeyStoreContainerType.JKS);
        assertThat(info.encoding()).isEqualTo(ContentEncoding.BINARY);
        assertThat(info.sourcePath()).isEqualTo("/tmp/a.jks");
        assertThat(info.sizeBytes()).isEqualTo(1024L);
        assertThat(info.integrityCheckPassed()).isTrue();
        assertThat(info.aliases()).containsExactly("alias-1");

        List<EntryAnalysis> entries = ctx.entries();
        assertThat(entries).hasSize(1);
        EntryAnalysis ea = entries.get(0);
        assertThat(ea.alias()).isEqualTo("alias-1");
        assertThat(ea.entryType()).isEqualTo(EntryType.TRUSTED_CERTIFICATE);
        assertThat(ea.certificate()).isSameAs(leaf);
        assertThat(ea.chain()).isNull();
        assertThat(ea.publicKeySha256Hex()).isNull();
        assertThat(ctx.runtime()).isSameAs(runtime());
    }

    @Test
    @DisplayName("null sourcePath is preserved for paste / in-memory keystores")
    void nullSourcePathPreserved() {
        InspectedKeyStore ins = inspected("k", EntryType.SECRET_KEY, null);
        KeyStoreLoadResult res = load(List.of(
                new LoadedEntry("k", EntryType.SECRET_KEY, new Date(),
                        List.of(), "AES", 256, true, List.of())));
        var ctx = RuleContextFactory.from(res, ins, ContentEncoding.BASE64,
                null, 0L, runtime());
        assertThat(ctx.loadedKeyStore().sourcePath()).isNull();
        assertThat(ctx.entries().get(0).certificate()).isNull();
    }

    /** Minimal X.509 stand-in — only the type matters for these factory tests. */
    private static final class DummyCert extends Certificate {
        DummyCert() { super("X.509"); }
        @Override public byte[] getEncoded() { return new byte[0]; }
        @Override public void verify(java.security.PublicKey key) { }
        @Override public void verify(java.security.PublicKey key, String sigProvider) { }
        @Override public java.security.PrivateKey getPrivateKey() { return null; }
        @Override public java.security.PublicKey getPublicKey() { return null; }
    }

    @Test
    @DisplayName("rejects failed load, null inspected, null runtime, null encoding")
    void rejectsBadInputs() {
        InspectedKeyStore ins = inspected("a", EntryType.TRUSTED_CERTIFICATE, leafAnalysis());
        KeyStoreLoadResult ok = load(List.of(
                LoadedEntry.trustedCertificate("a", new DummyCert(), new Date())));
        KeyStoreLoadResult bad = KeyStoreLoadResult.failure(
                io.github.certtool.domain.load.KeyFailure.of(
                        io.github.certtool.domain.error.LoadFailureReason.CORRUPTED, "x"));
        assertThatThrownBy(() -> RuleContextFactory.from(bad, ins, ContentEncoding.BINARY, null, 0L, runtime()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RuleContextFactory.from(null, ins, ContentEncoding.BINARY, null, 0L, runtime()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RuleContextFactory.from(ok, null, ContentEncoding.BINARY, null, 0L, runtime()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RuleContextFactory.from(ok, ins, null, null, 0L, runtime()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RuleContextFactory.from(ok, ins, ContentEncoding.BINARY, null, 0L, null))
                .isInstanceOf(NullPointerException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -pl app test -Dtest=RuleContextFactoryTest`
Expected: compile failure — `RuleContextFactory` does not exist.

- [ ] **Step 3: Implement `RuleContextFactory`**

Create `app/src/main/java/io/github/certtool/app/compliance/RuleContextFactory.java`:

```java
package io.github.certtool.app.compliance;

import io.github.certtool.domain.certificate.CertificateAnalysis;
import io.github.certtool.domain.context.EntryAnalysis;
import io.github.certtool.domain.context.LoadedKeyStoreInfo;
import io.github.certtool.domain.context.RuntimeEnvironment;
import io.github.certtool.domain.context.RuleContext;
import io.github.certtool.domain.inspect.InspectedCertificate;
import io.github.certtool.domain.inspect.InspectedEntry;
import io.github.certtool.domain.inspect.InspectedKeyStore;
import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.load.KeyStoreLoadResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds a {@link RuleContext} for the assessment engine from the inspected keystore pipeline
 * plus a fresh {@link RuntimeEnvironment} snapshot.
 *
 * <p>This class is pure: no JavaFX dependency, no I/O. All callers must supply a successful
 * {@link KeyStoreLoadResult} and a non-null {@link InspectedKeyStore}; otherwise the factory
 * throws to signal programmer error.
 */
public final class RuleContextFactory {

    private RuleContextFactory() {}

    public static RuleContext from(
            KeyStoreLoadResult load,
            InspectedKeyStore inspected,
            ContentEncoding encoding,
            String sourcePathOrNull,
            long sizeBytes,
            RuntimeEnvironment runtime) {
        Objects.requireNonNull(load, "load");
        Objects.requireNonNull(inspected, "inspected");
        Objects.requireNonNull(encoding, "encoding");
        Objects.requireNonNull(runtime, "runtime");
        if (!load.isSuccess()) {
            throw new IllegalArgumentException("load must be a successful KeyStoreLoadResult");
        }

        List<String> aliases = new ArrayList<>(inspected.entries().size());
        for (InspectedEntry e : inspected.entries()) {
            aliases.add(e.alias());
        }
        LoadedKeyStoreInfo info = new LoadedKeyStoreInfo(
                load.container(),
                encoding,
                sourcePathOrNull,
                sizeBytes,
                true,
                List.copyOf(aliases));

        List<EntryAnalysis> entries = new ArrayList<>(inspected.entries().size());
        for (InspectedEntry e : inspected.entries()) {
            CertificateAnalysis leaf = null;
            if (!e.certificates().isEmpty()) {
                InspectedCertificate c = e.certificates().get(0);
                leaf = c.analysis();
            }
            entries.add(new EntryAnalysis(e.alias(), e.entryType(), leaf, null, null));
        }

        return new RuleContext(info, List.copyOf(entries), runtime);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -pl app test -Dtest=RuleContextFactoryTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/certtool/app/compliance/RuleContextFactory.java \
        app/src/test/java/io/github/certtool/app/compliance/RuleContextFactoryTest.java
git commit -m "feat(app): add RuleContextFactory for compliance assessment"
```

---

## Task 6: Add helpers to `MainShellController` for Run + Export + view accessor

**Files:**
- Modify: `app/src/main/java/io/github/certtool/app/controller/MainShellController.java`

**Interfaces:**
- Produces (package-private for tests + same-class use):
  - `Node complianceView()` — returns the same `Node` instance each call; lazily builds it on first access.
  - `void runAssessment()` — triggered by Run button: builds `RuleContext` via `RuleContextFactory`, dispatches `AssessmentTask` on `composition.backgroundExecutor()`, wires status/progress, on success calls `complianceController.onReportProduced(...)`, on failure logs warning and updates status.
  - `void exportAssessmentReport()` — triggered by Export button (and menu): requires non-null report; shows `FileChooser`, infers `ExportReportTask.Format` from filter, submits task.
  - `void onKeyStoreChanged()` — calls `complianceController.onKeyStoreChanged()`.

- [ ] **Step 1: Add a field to cache the Compliance view**

Near the existing `private Node inspectViewNode;` field, add:

```java
private Node complianceViewNode;
```

- [ ] **Step 2: Add the `complianceView()` accessor**

Just below `Node inspectView()`, add:

```java
Node complianceView() {
    if (complianceViewNode == null) {
        complianceViewNode = buildComplianceView();
    }
    return complianceViewNode;
}
```

- [ ] **Step 3: Wire `buildComplianceView()` to use the new accessor**

In `buildContentTabs()`, change the `compliance.setContent(buildComplianceView());` line to:

```java
compliance.setContent(complianceView());
```

- [ ] **Step 4: Add `runAssessment()`**

Place near the other private handlers (e.g. right after `submitAnalyzeTask`):

```java
/** Builds a RuleContext from current Inspect state and dispatches an AssessmentTask. */
void runAssessment() {
    var inspectVm = composition.inspectVm();
    var load = inspectVm.getLoadResult();
    var inspected = inspectVm.getInspected();
    var encoding = inspectVm.getContentEncoding();
    if (load == null || !load.isSuccess() || inspected == null || encoding == null) {
        statusMessage.setText("Load and analyze a KeyStore first.");
        return;
    }
    Profile profile = composition.complianceVm().getSelectedProfile();
    if (profile == null) {
        statusMessage.setText("Select a profile.");
        return;
    }
    RuleContext ctx;
    try {
        ctx = RuleContextFactory.from(load, inspected, encoding,
                nullIfEmpty(inspectVm.getSourcePath()), 0L,
                io.github.certtool.app.platform.RuntimeInspector.capture());
    } catch (RuntimeException ex) {
        LOG.warn("Failed to build RuleContext", ex);
        statusMessage.setText("Assessment failed.");
        return;
    }
    AssessmentTask task = composition.assessTask(profile, ctx);
    task.stateProperty().addListener((obs, oldS, newS) -> updateProgress(newS, task.getProgress()));
    task.messageProperty().addListener((obs, oldM, newM) -> {
        if (newM != null && !newM.isEmpty()) {
            statusMessage.setText(newM);
        }
    });
    task.setOnSucceeded(evt -> {
        AssessmentReport report = task.getValue();
        composition.complianceController().onReportProduced(report);
        statusMessage.setText("Assessment complete: " + report.findings().size() + " finding(s).");
    });
    task.setOnFailed(evt -> statusMessage.setText("Assessment failed."));
    composition.backgroundExecutor().submit(task);
}

private static String nullIfEmpty(String s) {
    return s == null || s.isEmpty() ? null : s;
}
```

- [ ] **Step 5: Add `exportAssessmentReport()`**

```java
/** File-chooser-driven export of the current AssessmentReport. */
void exportAssessmentReport() {
    AssessmentReport report = composition.complianceVm().getReport();
    if (report == null) {
        statusMessage.setText("Run an assessment before exporting.");
        return;
    }
    FileChooser chooser = new FileChooser();
    chooser.setTitle("Export FIPS Compatibility Assessment Report");
    chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("JSON (*.json)", "*.json"),
            new FileChooser.ExtensionFilter("HTML (*.html)", "*.html"),
            new FileChooser.ExtensionFilter("Markdown (*.md)", "*.md"));
    chooser.setSelectedExtensionFilter(chooser.getExtensionFilters().get(0));
    File chosen = chooser.showSaveDialog(stage);
    if (chosen == null) {
        LOG.debug("Export Report dialog cancelled.");
        return;
    }
    ExportReportTask.Format fmt = inferFormat(chosen, chooser.getSelectedExtensionFilter());
    String title = "FIPS Compatibility Assessment - " + report.profile().name();
    String sourceLabel = reportSourceLabel(composition.inspectVm().getSourcePath());
    ReportEnvelope env = new ReportEnvelope.AssessmentReportEnvelope(title, sourceLabel, report);
    ExportReportTask task = composition.exportTask(env, chosen.toPath(), fmt);
    task.messageProperty().addListener((obs, oldM, newM) -> {
        if (newM != null && !newM.isEmpty()) {
            statusMessage.setText(newM);
        }
    });
    task.setOnSucceeded(evt -> statusMessage.setText("Exported report to " + chosen));
    task.setOnFailed(evt -> statusMessage.setText("Export failed."));
    composition.backgroundExecutor().submit(task);
}

private static ExportReportTask.Format inferFormat(File chosen, FileChooser.ExtensionFilter filter) {
    String name = chosen.getName().toLowerCase(java.util.Locale.ROOT);
    if (filter != null && filter.getDescription() != null) {
        String d = filter.getDescription().toLowerCase(java.util.Locale.ROOT);
        if (d.startsWith("json")) return ExportReportTask.Format.JSON;
        if (d.startsWith("html")) return ExportReportTask.Format.HTML;
        if (d.startsWith("markdown")) return ExportReportTask.Format.MARKDOWN;
    }
    if (name.endsWith(".json")) return ExportReportTask.Format.JSON;
    if (name.endsWith(".html")) return ExportReportTask.Format.HTML;
    if (name.endsWith(".md")) return ExportReportTask.Format.MARKDOWN;
    return ExportReportTask.Format.JSON;
}

private static String reportSourceLabel(String sourcePath) {
    if (sourcePath == null || sourcePath.isEmpty()) {
        return "pasted-base64";
    }
    return sourcePath;
}
```

- [ ] **Step 6: Add `onKeyStoreChanged()` shell hook**

```java
/** Notifies the compliance view that a new keystore has been loaded. */
void onKeyStoreChanged() {
    composition.complianceController().onKeyStoreChanged();
}
```

- [ ] **Step 7: Compile the app module**

Run: `./mvnw -pl app -am compile`
Expected: BUILD SUCCESS.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/io/github/certtool/app/controller/MainShellController.java
git commit -m "feat(app): add runAssessment, exportAssessmentReport, and complianceView helper"
```

---

## Task 7: Replace `buildComplianceView()` with the real layout

**Files:**
- Modify: `app/src/main/java/io/github/certtool/app/controller/MainShellController.java`

- [ ] **Step 1: Implement the full layout**

Replace the current `buildComplianceView()` body (currently a placeholder) with the layout below. Preserve the existing method signature.

```java
private Node buildComplianceView() {
    BorderPane shell = new BorderPane();
    shell.setPadding(new Insets(12));

    // ---- TOP: profile + actions + disclaimer ---------------------------------------
    VBox top = new VBox(8);

    HBox actions = new HBox(8);
    actions.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
    ComboBox<Profile> profileCombo = new ComboBox<>();
    profileCombo.setId("complianceProfileCombo");
    profileCombo.setItems(composition.complianceVm().availableProfiles());
    profileCombo.valueProperty().bindBidirectional(composition.complianceVm().selectedProfileProperty());

    Button runButton = new Button("Run Assessment");
    runButton.setId("complianceRunButton");
    runButton.setOnAction(evt -> runAssessment());

    Button exportButton = new Button("Export Report…");
    exportButton.setId("complianceExportButton");
    exportButton.setOnAction(evt -> exportAssessmentReport());

    actions.getChildren().addAll(new Label("Profile:"), profileCombo, runButton, exportButton);

    TextArea disclaimer = new TextArea(io.github.certtool.domain.assessment.FipsDisclaimer.text());
    disclaimer.setId("complianceDisclaimer");
    disclaimer.setWrapText(true);
    disclaimer.setEditable(false);
    disclaimer.setPrefRowCount(3);

    top.getChildren().addAll(actions, disclaimer);

    // ---- MIDDLE: summary chips -----------------------------------------------------
    HBox chips = new HBox(8);
    chips.setId("complianceSummaryChips");
    Label[] chipLabels = new Label[AssessmentStatus.values().length];
    for (int i = 0; i < AssessmentStatus.values().length; i++) {
        AssessmentStatus s = AssessmentStatus.values()[i];
        Label l = new Label(formatChip(s, 0L));
        l.setId("complianceChip-" + s.name());
        chipLabels[i] = l;
        chips.getChildren().add(l);
    }
    composition.complianceVm().reportProperty().addListener((obs, oldR, newR) -> {
        var counts = composition.complianceVm().summaryCounts();
        for (int i = 0; i < AssessmentStatus.values().length; i++) {
            AssessmentStatus s = AssessmentStatus.values()[i];
            chipLabels[i].setText(formatChip(s, counts.getOrDefault(s, 0L)));
        }
    });

    // ---- FILTER ROW ---------------------------------------------------------------
    HBox filterRow = new HBox(8);
    filterRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
    TextField filterField = new TextField();
    filterField.setId("complianceFilterField");
    filterField.setPromptText("Filter by rule id, title, or text");
    filterField.textProperty().bindBidirectional(composition.complianceVm().filterTextProperty());

    ComboBox<Severity> severityCombo = new ComboBox<>();
    severityCombo.setId("complianceSeverityCombo");
    severityCombo.getItems().addAll(Severity.values());
    severityCombo.valueProperty().bindBidirectional(composition.complianceVm().minSeverityFilterProperty());
    filterRow.getChildren().addAll(new Label("Filter:"), filterField, new Label("Min severity:"), severityCombo);

    // ---- TABLE + DETAIL ----------------------------------------------------------
    TableView<AssessmentFinding> table = new TableView<>();
    table.setId("complianceFindingsTable");
    table.setItems(composition.complianceVm().filteredFindings());
    table.setPlaceholder(new Label("Run an assessment after loading a KeyStore."));
    TableColumn<AssessmentFinding, AssessmentStatus> colStatus = new TableColumn<>("Status");
    colStatus.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue().status()));
    TableColumn<AssessmentFinding, Severity> colSev = new TableColumn<>("Severity");
    colSev.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue().severity()));
    TableColumn<AssessmentFinding, String> colRule = new TableColumn<>("Rule");
    colRule.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().ruleId()));
    TableColumn<AssessmentFinding, String> colTitle = new TableColumn<>("Title");
    colTitle.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().title()));
    table.getColumns().addAll(colStatus, colSev, colRule, colTitle);
    table.getSelectionModel().selectedItemProperty().addListener((obs, oldF, newF) ->
            composition.complianceController().onFindingSelected(newF));

    VBox detail = new VBox(4);
    detail.setId("complianceDetail");
    Label detailRule = new Label();
    detailRule.setId("complianceDetailRule");
    Label detailStatus = new Label();
    Label detailSev = new Label();
    TextArea detailSummary = new TextArea();
    detailSummary.setEditable(false);
    detailSummary.setWrapText(true);
    detailSummary.setPrefRowCount(2);
    TextArea detailEvidence = new TextArea();
    detailEvidence.setEditable(false);
    detailEvidence.setWrapText(true);
    detailEvidence.setPrefRowCount(3);
    TextArea detailRemediation = new TextArea();
    detailRemediation.setEditable(false);
    detailRemediation.setWrapText(true);
    detailRemediation.setPrefRowCount(3);
    Label detailReferences = new Label();
    detail.getChildren().addAll(
            new Label("Detail"),
            detailRule, detailStatus, detailSev,
            new Label("Summary:"), detailSummary,
            new Label("Evidence:"), detailEvidence,
            new Label("Remediation:"), detailRemediation,
            new Label("References:"), detailReferences);

    composition.complianceVm().selectedFindingProperty().addListener((obs, oldF, f) -> {
        if (f == null) {
            detailRule.setText("");
            detailStatus.setText("");
            detailSev.setText("");
            detailSummary.setText("");
            detailEvidence.setText("");
            detailRemediation.setText("");
            detailReferences.setText("");
        } else {
            detailRule.setText(f.ruleId() + " — " + f.title());
            detailStatus.setText("Status: " + f.status());
            detailSev.setText("Severity: " + f.severity());
            detailSummary.setText(f.summary());
            detailEvidence.setText(f.evidence());
            detailRemediation.setText(f.remediation());
            detailReferences.setText(String.join(", ", f.references()));
        }
    });

    SplitPane split = new SplitPane();
    split.getItems().addAll(table, detail);
    split.setDividerPosition(0, 0.55);

    VBox content = new VBox(8, top, chips, filterRow, split);
    shell.setCenter(content);

    // Initial enablement based on whether a report exists.
    exportButton.disableProperty().bind(
            composition.complianceVm().reportProperty().isNull());

    return shell;
}

private static String formatChip(AssessmentStatus s, long count) {
    return s.name() + ": " + count;
}
```

Required new imports (top of `MainShellController.java`, grouped with existing imports):

```java
import io.github.certtool.app.compliance.RuleContextFactory;
import io.github.certtool.app.task.AssessmentTask;
import io.github.certtool.app.task.ExportReportTask;
import io.github.certtool.domain.assessment.AssessmentReport;
import io.github.certtool.domain.assessment.AssessmentStatus;
import io.github.certtool.domain.assessment.Severity;
import io.github.certtool.domain.context.RuleContext;
import io.github.certtool.domain.profile.Profile;
import io.github.certtool.reporting.ReportEnvelope;
import javafx.scene.control.ComboBox;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import java.io.File;
```

If any of these are already imported, do not duplicate.

- [ ] **Step 2: Wire the File → Export Report menu to the same handler**

Replace the existing menu wiring:

```java
MenuItem export = new MenuItem("Export Report…");
export.setOnAction(evt -> exportAssessmentReport());
```

(Previously it logged an "Export Report not yet implemented" message.)

- [ ] **Step 3: Call `onKeyStoreChanged()` whenever a new keystore loads**

In `onOpenKeyStore` immediately after `recordLoadSource(...)` (Task 2), add:

```java
onKeyStoreChanged();
```

In `handlePastedLoadResult` and `submitSelectedContainerLoad`, do the same after each successful load.

- [ ] **Step 4: Compile and run app tests**

Run: `./mvnw -pl app -am compile && ./mvnw -pl app test`
Expected: BUILD SUCCESS, all tests PASS.

- [ ] **Step 5: Run `./mvnw spotless:apply`**

Run: `./mvnw spotless:apply`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/io/github/certtool/app/controller/MainShellController.java
git commit -m "feat(app): implement Compliance tab UI with Run, Export, and findings detail"
```

---

## Task 8: Add `complianceView()` smoke test

**Files:**
- Modify: `app/src/test/java/io/github/certtool/app/controller/MainShellControllerTest.java`

- [ ] **Step 1: Add failing tests**

Append to `MainShellControllerTest` (inside the same `class`):

```java
@Test
@DisplayName("complianceView returns a non-null Node containing the disclaimer and chips")
void complianceViewReturnsNode() throws Exception {
    MainShellController controller = new MainShellController(
            composition(new RecordingExecutor()), null);
    Node view = controller.complianceView();
    assertThat(view).isNotNull();
    assertThat(findNode(view, TextArea.class)).isNotNull();
    assertThat(findNode(view, javafx.scene.control.TableView.class)).isNotNull();
    assertThat(findLabel(view, "Run Assessment")).isNotNull();
    assertThat(findLabel(view, "Export Report…")).isNotNull();
}

@Test
@DisplayName("onKeyStoreChanged clears any prior assessment report on the compliance VM")
void onKeyStoreChangedClearsReport() throws Exception {
    AppComposition comp = composition(new RecordingExecutor());
    comp.complianceController().onReportProduced(new AssessmentReport(
            io.github.certtool.compliance.loader.DefaultProfiles.loadFips1403(),
            java.time.Instant.parse("2026-07-15T00:00:00Z"),
            java.util.List.of(new io.github.certtool.domain.assessment.AssessmentFinding(
                    "R-1", "x", io.github.certtool.domain.assessment.AssessmentStatus.PASS,
                    io.github.certtool.domain.assessment.Severity.INFO,
                    "s", "e", "r", java.util.List.of()))));
    MainShellController controller = new MainShellController(comp, null);
    controller.onKeyStoreChanged();
    assertThat(comp.complianceVm().getReport()).isNull();
    assertThat(comp.complianceVm().filteredFindings()).isEmpty();
}
```

(Re-use the existing `composition(RecordingExecutor)`, `findLabel`, `findNode` helpers in this test file.)

- [ ] **Step 2: Run tests to verify they pass**

Run: `./mvnw -pl app test -Dtest=MainShellControllerTest`
Expected: PASS. If `TextArea` is not yet imported, add `import javafx.scene.control.TextArea;`.

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/io/github/certtool/app/controller/MainShellControllerTest.java
git commit -m "test(app): cover complianceView and onKeyStoreChanged on MainShellController"
```

---

## Task 9: Full `./mvnw verify` + manual smoke

**Files:**
- No source changes expected.

- [ ] **Step 1: Run spotless and full verify**

```bash
./mvnw spotless:apply
./mvnw verify
```

Expected: BUILD SUCCESS. All gates (Spotless, Checkstyle/SpotBugs, JaCoCo, OWASP, CycloneDX) green.

- [ ] **Step 2: Manual smoke (informational)**

```bash
./mvnw -pl app -am javafx:run
```

Walk through: open a JKS or BCFKS file → switch to Compliance tab → pick `FIPS_140_3_ASSESSMENT` → click Run Assessment → verify summary chips update and the table shows findings → filter by "RSA" → click a finding → verify detail pane shows summary/evidence/remediation/references → click Export Report… → save as JSON → open the file → confirm disclaimer is present and no secrets leak → repeat for HTML and Markdown.

- [ ] **Step 3: Final commit (docs / chore if needed)**

If any docs (`cert_tool.md`, `AGENTS.md`, `.superpowers/sdd/task-N-report.md`) need a status update to reflect the new feature, do so as a separate commit:

```bash
git add docs cert_tool.md AGENTS.md .superpowers/sdd/
git commit -m "docs: mark compliance tab feature complete"
```

---

## Self-Review Notes

- **Spec coverage:**
  - Profile picker / Run / Export / chips / filter / table / detail / disclaimer — covered by Tasks 1, 3, 6, 7.
  - RuleContext mapping from Inspect data — Task 5 (factory) + Task 2 (encoding/path tracking).
  - Clear on new keystore — Task 4 (controller) + Task 6 (`onKeyStoreChanged` shell) + Task 7 (call sites).
  - Export Report wired from File menu — Task 7.
  - Security constraints: ensured by existing engine + reporting (no change). Factory passes only public cert analyses + null chain — no key material.
- **Type / property consistency:** `contentEncodingProperty`, `sourcePathProperty`, `selectedFindingProperty`, `clearReport`, `onKeyStoreChanged`, `runAssessment`, `exportAssessmentReport`, `complianceView`, `RuleContextFactory.from(...)` are all used consistently across Tasks 1–8.
- **Placeholder scan:** No TBD/TODO steps. Every code block contains the full code; every command has an expected outcome.