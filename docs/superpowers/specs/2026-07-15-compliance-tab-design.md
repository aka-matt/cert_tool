# Compliance Tab Design

## Goal

Ship a complete **Compliance** tab for FIPS Compatibility Assessment: profile selection, manual Run Assessment, Export Report (JSON / HTML / Markdown), summary cards, filterable findings table, evidence/remediation detail pane, and the mandatory FIPS disclaimer.

The tab consumes an already-loaded and analyzed keystore (from the Inspect pipeline). It does **not** implement the Inspect-page collapsible Compliance Inspector (right pane remains the existing deferred stub).

Function names and UI copy use **FIPS Compatibility Assessment** / **FIPS Readiness Assessment** only. Never claim FIPS Certification, Official FIPS Validation, NIST Certified, or 正式认证结论.

## Scope

**In scope**

- Full Compliance tab UI bound to `ComplianceViewModel` / `ComplianceController`
- Manual Run Assessment only (user selects Profile and clicks Run)
- `RuleContext` built from Inspect data (`KeyStoreLoadResult` + `InspectedKeyStore` + live `RuntimeEnvironment`)
- Export Report via file chooser with format extension filters (JSON / HTML / Markdown)
- Wire existing **File → Export Report…** to the same export path
- Clear stale assessment report when a new keystore is loaded
- Unit tests for mapper, controller/VM extensions, and non-TestFX shell wiring checks
- Design doc under `docs/superpowers/specs/`

**Out of scope**

- Inspect right-pane Compliance Inspector
- Auto-run assessment after analyze or on profile change
- Menu **Actions → Run FIPS Assessment** (tab button is sufficient for this delivery)
- Custom profile import UI
- Populating `EntryAnalysis.chain` via `ChainAnalyzer` (current rules do not read `chain()`)
- Convert wizard / Runtime page redesign

## Architecture (Approach A)

Thin MainShell view + pure RuleContext mapper. Reuse existing engine, tasks, reporting, and view-model filtering.

| Module | Change |
| --- | --- |
| `compliance-engine` | None — `AssessmentEngine`, profiles, and rules already exist |
| `reporting` | None — `ReportEnvelope` + JSON/HTML/Markdown renderers already exist |
| `domain` | None required for v1 mapping (chain left null) |
| `app` | Main work: UI, `RuleContextFactory`, small VM/controller extensions, menu export wiring |

### Data flow

```
Load keystore → AnalyzeKeyStoreTask → InspectViewModel (loadResult + inspected + encoding + sourcePath)
User opens Compliance tab, picks Profile, clicks Run Assessment
  → RuleContextFactory.from(load, inspected, encoding, sourcePath, sizeBytes, RuntimeInspector.capture())
  → AssessmentTask (backgroundExecutor)
  → ComplianceController.onReportProduced(report)
  → summary cards + filtered table + detail pane
Export Report
  → ReportEnvelope.AssessmentReportEnvelope(title, sourceLabel, report)
  → ExportReportTask (atomic write)
```

### Preconditions

- **Run Assessment** enabled only when `loadResult` is success **and** `inspected != null` **and** a profile is selected **and** no assessment task is currently running.
- **Export Report** enabled only when `ComplianceViewModel.getReport() != null` **and** no export task is currently running.
- If a disabled path somehow fires: status message only (e.g. “Load and analyze a KeyStore first.” / “Run an assessment before exporting.”).

## RuleContext mapping

New pure class:

`app/src/main/java/io/github/certtool/app/compliance/RuleContextFactory.java`

```text
static RuleContext from(
    KeyStoreLoadResult load,       // must be success
    InspectedKeyStore inspected,   // non-null
    ContentEncoding encoding,
    String sourcePathOrNull,       // file path, or null for paste / in-memory
    long sizeBytes,                // 0 when unknown
    RuntimeEnvironment runtime)
```

### LoadedKeyStoreInfo

| Field | Source |
| --- | --- |
| `containerType` | `load.container()` |
| `encoding` | caller-supplied `ContentEncoding` (tracked on Inspect after load) |
| `sourcePath` | tracked path, or `null` for paste |
| `sizeBytes` | `0` when not tracked (rules do not use size today) |
| `integrityCheckPassed` | `true` for a successful load (integrity was verified by the loader) |
| `aliases` | ordered aliases from `load.entries()` (or inspected entries in stable order) |

### EntryAnalysis (per InspectedEntry)

| Field | Source |
| --- | --- |
| `alias` | `InspectedEntry.alias()` |
| `entryType` | `InspectedEntry.entryType()` |
| `certificate` | first `InspectedCertificate.analysis()` when chain non-empty; else `null` |
| `chain` | always `null` in this delivery |
| `publicKeySha256Hex` | always `null` in this delivery (rules use `CertificateAnalysis` fields) |

### RuntimeEnvironment

`RuntimeInspector.capture()` at Run time (fresh snapshot), not a cached Runtime-tab value, so provider state matches the assessment moment.

### Validation

- Failed or null `load` → `IllegalArgumentException` (caller must not invoke when disabled).
- Null `inspected` or `runtime` or `encoding` → `IllegalArgumentException`.

## UI layout

`MainShellController.buildComplianceView()` returns a `BorderPane` (or `VBox` + `SplitPane`) with:

```
┌─ Compliance ────────────────────────────────────────────────────────────┐
│ Profile: [ FIPS_140_3_ASSESSMENT ▼ ]  [ Run Assessment ]  [ Export… ] │
│                                                                         │
│ Disclaimer (always visible, wrap):                                      │
│   FipsDisclaimer.text()                                                 │
│                                                                         │
│ ┌ PASS ┐ ┌ WARNING ┐ ┌ FAIL ┐ ┌ NOT_APPLICABLE ┐ ┌ NOT_ASSESSABLE ┐  │
│                                                                         │
│ Filter: [____________]  Min severity: [ INFO ▼ ]                        │
│                                                                         │
│ ┌ Findings Table ──────────────────┬ Detail ──────────────────────────┐ │
│ │ Status │ Sev │ RuleId │ Title    │ ruleId, title, status, severity  │ │
│ │ …      │ …   │ …      │ …        │ summary, evidence, remediation,  │ │
│ │        │     │        │          │ references                       │ │
│ └──────────────────────────────────┴──────────────────────────────────┘ │
│ empty (no report): “Run an assessment after loading a KeyStore.”        │
└─────────────────────────────────────────────────────────────────────────┘
```

### Controls and binding

| Control | Behavior |
| --- | --- |
| Profile `ComboBox<Profile>` | items = `availableProfiles()`; selection → `ComplianceController.onProfileSelected` |
| Run Assessment button | enabled per preconditions; submits `composition.assessTask(profile, ctx)` |
| Export Report… button | enabled when report present; FileChooser with format filters |
| Disclaimer | always shows `FipsDisclaimer.text()` (not only after run) |
| Summary chips | `summaryCounts()` for all five `AssessmentStatus` values; zeros when no report |
| Filter text field | → `onFilterTextChanged` (existing case-insensitive match on ruleId/title/summary) |
| Min severity combo | → `onMinSeverityChanged` (existing ordinal filter) |
| Findings `TableView` | items = `filteredFindings()`; columns Status, Severity, RuleId, Title |
| Detail pane | selected finding → full fields; empty when none selected |

Set stable `Node.setId(...)` on key controls for unit tests without TestFX where practical (`complianceProfileCombo`, `complianceRunButton`, `complianceExportButton`, `complianceFindingsTable`, etc.).

### Menu

- **File → Export Report…** uses the same export handler as the tab button (status message when no report).
- No new top-level Run menu item in this delivery.

## Shell wiring

### On successful load + analyze

Existing flow already sets `InspectController.onLoadResult` and `applyInspection`. Extend to also:

1. Record `ContentEncoding` and `sourcePath` (null for paste) on `InspectViewModel`.
2. Call `ComplianceController.onKeyStoreChanged()` (or equivalent) to **clear** any previous `AssessmentReport` so findings never describe a prior store.

### Run Assessment handler

1. Read profile from VM; abort with status if null.
2. Read load + inspected + encoding + path from Inspect VM; abort if not ready.
3. Build `RuleContext` via `RuleContextFactory`.
4. Create `AssessmentTask` via `AppComposition.assessTask`.
5. Bind progress/message to status bar (same pattern as load/analyze).
6. On success: `complianceController.onReportProduced(report)`; status with finding count.
7. On failure: leave previous report; status “Assessment failed.”; optional Alert with non-sensitive message only.
8. Disable Run while task is running; re-enable when terminal.

### Export handler

1. Require non-null report.
2. `FileChooser` with filters:
   - JSON (`*.json`)
   - HTML (`*.html`)
   - Markdown (`*.md`)
3. Infer `ExportReportTask.Format` from selected filter / extension (default JSON if ambiguous).
4. `sourceLabel` = path string if present, else `"pasted-base64"` or `"in-memory"` — never the raw Base64.
5. Title e.g. `"FIPS Compatibility Assessment — " + profile.name()`.
6. Wrap `new ReportEnvelope.AssessmentReportEnvelope(title, sourceLabel, report)`.
7. Submit `ExportReportTask`; on success status with target path; on failure status “Export failed.”

## View-model / controller extensions

### InspectViewModel

Add (or equivalent properties):

- `ObjectProperty<ContentEncoding> contentEncoding` (default / clear on new load)
- `StringProperty sourcePath` (empty or null for paste)

Set from MainShell on file open vs paste paths.

### ComplianceViewModel

- Existing: profile, report, filter text, min severity, filtered findings, summaryCounts — keep.
- Add: `ObjectProperty<AssessmentFinding> selectedFinding` (optional but recommended for detail pane binding).
- Add: `clearReport()` → `setReport(null)` and clear selection.
- Optional helpers: `boolean canExport()` based on report non-null (Run readiness stays in shell/controller because it depends on Inspect state).

### ComplianceController

- Existing: `onProfileSelected`, `onReportProduced`, `onFilterTextChanged`, `onMinSeverityChanged` — keep.
- Add: `onKeyStoreChanged()` → clear report/selection.
- Add: `onFindingSelected(AssessmentFinding)` → update selected finding property.

## Error handling

| Situation | Behavior |
| --- | --- |
| No keystore / analyze incomplete | Run disabled; empty-state copy |
| No report | Export disabled; empty findings area |
| Assessment task fails | Keep previous report; status “Assessment failed.” |
| Export cancelled | No-op |
| Export render/sanitize fails | Status “Export failed.”; no partial final file (atomic write already in `ExportReportTask`) |
| New keystore loaded | Clear compliance report immediately |

## Security constraints

Per `cert_tool.md` §2 / CLAUDE.md:

1. Never log store password, key password, private key, secret key, full Base64, or full keystore binary.
2. Status text, dialogs, and `sourceLabel` must not include pasted Base64 or passwords.
3. Reports always carry `FipsDisclaimer`; UI always shows the disclaimer.
4. Export uses atomic write already implemented by `ExportReportTask`.
5. Findings fields from the engine must remain free of secrets (engine already constrained).

## Test strategy (TDD)

No new TestFX for this delivery. Prefer plain unit tests.

1. **`RuleContextFactoryTest`**
   - Mixed private-key / trusted / secret entries map correctly.
   - Leaf certificate analysis attached when present; secret keys get null certificate.
   - `integrityCheckPassed == true` on successful load.
   - Aliases preserved; chain and publicKeySha256Hex null.
   - Rejects failed load / null inspected.

2. **`ComplianceControllerTest` / `ComplianceViewModelTest` (extend)**
   - `onKeyStoreChanged` clears report and filtered list.
   - Selected finding updates detail property.
   - Existing filter / severity / summary tests remain green.

3. **`MainShellController` / view construction (unit-level where feasible)**
   - `buildComplianceView()` returns non-null node.
   - Optional: lookup by `Node.getId` for profile combo / run / export if controls are attached without showing a Stage.

4. **Security**
   - If a natural hook exists, extend log-capture tests so a run+export path does not emit secrets. Do not invent fragile full-UI security tests.

## Expected file list

**New**

- `app/src/main/java/io/github/certtool/app/compliance/RuleContextFactory.java`
- `app/src/test/java/io/github/certtool/app/compliance/RuleContextFactoryTest.java`
- `docs/superpowers/specs/2026-07-15-compliance-tab-design.md` (this file)

**Changed**

- `app/src/main/java/io/github/certtool/app/controller/MainShellController.java` — real Compliance view; Run/Export handlers; clear report on load; menu export
- `app/src/main/java/io/github/certtool/app/controller/ComplianceController.java` — clear + selection
- `app/src/main/java/io/github/certtool/app/viewmodel/ComplianceViewModel.java` — selected finding / clear helpers
- `app/src/main/java/io/github/certtool/app/viewmodel/InspectViewModel.java` — encoding + sourcePath
- `app/src/test/java/io/github/certtool/app/controller/ComplianceControllerTest.java`
- Possibly `app/src/test/java/io/github/certtool/app/controller/MainShellControllerTest.java`
- Possibly `app/src/test/java/io/github/certtool/app/viewmodel/InspectViewModelTest.java` / Compliance VM tests

**Unchanged**

- `compliance-engine/**` (unless a bug is discovered)
- `reporting/**`
- Inspect right-pane stub

## Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Stale findings after reload | Clear report on every new load |
| Missing encoding/path for paste | Track on InspectViewModel; sourceLabel fallback `"pasted-base64"` |
| MainShell grows further | Keep layout in one private method; pure logic stays in factory/controller |
| Rules later need chain analysis | Follow-up can extend factory to call `ChainAnalyzer` without UI change |
| FIPS wording misuse | Hard-code disclaimer widget; report already embeds disclaimer |

## Success criteria

- After loading and analyzing a keystore, user can select a built-in profile, run assessment, see summary counts and findings, filter them, inspect evidence/remediation, and export JSON/HTML/Markdown.
- Disclaimer is always visible on the tab and present in exported reports.
- No secrets in logs, status text, or reports.
- `./mvnw test -pl app,domain,compliance-engine,reporting` relevant modules pass; full `./mvnw verify` before claiming done.
- Inspect right pane remains deferred; Convert/Runtime unchanged beyond shared menu export wiring.
