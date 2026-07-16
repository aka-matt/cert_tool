# Convert Tab Design

Date: 2026-07-15
Status: Draft (awaiting user review)
Branch: `step9`
Sources: `cert_tool.md` §8 (Conversion); `CLAUDE.md` (module boundaries, security rules, testing).

## 1. Goal

Wire the existing conversion engine (`KeystoreConversion.execute`) to a 5-step
JavaFX wizard — Source / Contents / Target / Preflight / Execute — that
satisfies all of cert_tool.md §8 and Completion Criteria item 3 ("all four
forms convertible"). The wizard is purely UI/state; the engine is unchanged.

## 2. Decisions already made (this brainstorm)

* **Source model:** Pre-loaded from Inspect. The wizard does NOT contain a file
  chooser or paste dialog. Step 1 is a read-only card populated from
  `InspectViewModel.loadResult` / `InspectedKeyStore`. If nothing is loaded, an
  empty state invites the user to open a keystore on the Inspect tab.
* **Preflight timing:** Implicit. When `currentStep` transitions to PREFLIGHT,
  the wizard submits `ConvertPreflightTask` immediately. BLOCK findings disable
  the Next button. WARN findings render as a yellow list. Re-running is supported
  via "Re-run preflight".
* **Per-entry passwords:** Lazy. The wizard does NOT prompt for entry passwords
  in Step 2. Step 4 surfaces any "needs entry password" preflight findings as
  inline `PasswordField` rows; only those aliases are prompted for.
* **FIPS profile:** Reuse the global `ComplianceViewModel.selectedProfile`.
  Step 4 shows the profile name read-only. No profile selector inside the
  wizard.
* **Step 5 surface:** Compact success card — written path + bytes, "Open
  containing folder" / "Copy path", 5-row verification table, WARN list, and a
  secondary "Run Compliance assessment on the new target" button.
  No export-to-JSON on this step (compliance reports export from Compliance).
* **Architecture:** Pattern A — `WizardStep` enum on the VM, navigation via
  derived properties, `Map<WizardStep, Node>` cache for step panels.

## 3. Module layout

### 3.1 New files (all under `app/src/main/java/io/github/certtool/app/`)

| File | Type | Purpose |
|---|---|---|
| `viewmodel/ConvertWizardViewModel.java` | extends `ConvertViewModel` | wizard step enum, navigation state, derived bindings |
| `controller/ConvertWizardController.java` | pure logic | validators, plan builder, preflight orchestration |
| `view/ConvertView.java` | UI builder | 5 step panels, header/footer, bindings |
| `task/ConvertPreflightTask.java` | `Task<PreflightReport>` | background preflight runner |

Tests mirror under `app/src/test/java/io/github/certtool/app/...`.

### 3.2 New types — minimal, app-module

* `WizardStep` and `record Base64Options { int lineWidth; boolean wrapHeaders; }`
  both live in `app/viewmodel/ConvertWizardViewModel.java` (app module — no
  business meaning outside the UI; only one consumer). The engine's preflight
  does not consult base64 layout — that responsibility lives in
  `KeyStoreGenerator.toBytes`/`loadBytes`, which currently does not read
  line width or PEM-style headers from any structured input. The wizard's
  Base64 options will be honored in a follow-on Phase 5 ADR; for the first
  cut, the wizard records them on the VM and persists them on `lastResult`
  only for the user to review in the success card.
* No new `domain` types. `TargetFormat` would have a single caller
  (the wizard); the wizard uses the existing `KeyStoreContainerType` +
  `ContentEncoding` pair inline. `WizardStep` is intentionally app-module so
  it does not pollute `domain`.

### 3.3 Modified files

* `app/.../controller/MainShellController.java` —
  * add `Node convertView()` package-private accessor (lazy-build via
    `buildConvertView()`, mirrors `complianceView()`),
  * wire `composition.inspectVm().loadResultProperty()` (success only) to
    `convertController.onSourceSelected(info)` and the new wizard's
    `resetOnSourceChange()`,
  * add Pattern B re-entry guard (`currentConvertTask`,
    `convertButton.setDisable(true/false)` mirroring `currentAssessTask`).
* `app/.../AppComposition.java` — instantiate
  `ConvertWizardViewModel`/`ConvertWizardController`/`ConvertView`; expose
  `convertWizardVm()` and a `convertWizardController()` accessor on the
  composition. The existing `convertVm()` accessor is removed (or repointed
  to `convertWizardVm`); the wizard IS the convert VM from this point on.
* `app/.../controller/ConvertController.java` — unchanged; its `onSourceSelected`,
  `onPreflightProduced`, etc. are reused as-is.

### 3.4 Unchanged (engine primitives already exist)

* `conversion/.../core/KeystoreConversion.java`
* `conversion/.../core/Preflight.java`
* `conversion/.../core/AliasResolver.java`
* `conversion/.../core/AtomicWriter.java`
* `conversion/.../core/EntryCopy.java`
* `conversion/.../domain/plan/ConversionPlan.java`
* `conversion/.../domain/plan/AliasConflictPolicy.java`
* `conversion/.../domain/plan/OverwritePolicy.java`
* `conversion/.../domain/preflight/PreflightReport.java`
* `conversion/.../domain/preflight/PreflightFinding.java`
* `conversion/.../domain/preflight/PreflightSeverity.java`
* `conversion/.../domain/result/ConversionResult.java`
* `conversion/.../domain/result/ReloadVerification.java`
* `app/.../task/ConvertTask.java` (reused by the wizard's "Convert" button)
* `app/.../viewmodel/ConvertViewModel.java` (extended, not modified)

## 4. Wizard state machine

### 4.1 `ConvertWizardViewModel`

```java
public enum WizardStep { SOURCE, CONTENTS, TARGET, PREFLIGHT, EXECUTE }

public final class ConvertWizardViewModel extends ConvertViewModel {
    private final ObjectProperty<WizardStep> currentStep = /* default SOURCE */;
    private final ReadOnlyBooleanProperty nextEnabled;       // per-step validator
    private final ReadOnlyBooleanProperty backEnabled;       // false on SOURCE only
    private final BooleanProperty runningPreflight = new SimpleBooleanProperty();
    private final BooleanProperty runningConvert   = new SimpleBooleanProperty();
    private final ReadOnlyStringProperty stepTitle;         // derived from currentStep
    // plus observable ObjectProperty<Base64Options> at the VM level (target-side only)
}
```

### 4.2 Per-step `nextEnabled` predicate (single source of truth, in `ConvertWizardController`)

| Step | nextEnabled when… |
|---|---|
| SOURCE   | `getSource() != null` (always true when populated from Inspect). |
| CONTENTS | `selectedAliases` is non-empty. |
| TARGET   | target container chosen + encoding chosen + `targetPath` non-empty + (target store password entered OR target is a truststore with no private entries) + alias-conflict policy chosen + overwrite policy chosen. |
| PREFLIGHT | preflight ran (`getPreflightReport() != null`) AND `!report.hasBlockers()`. The optional `entryOverridePassword` field is recorded on the plan if non-empty; absent that, the engine's `PasswordProvider` prompts per-entry at execution time inside `EntryCopy.copy(...)` and the wizard has nothing to gate. |
| EXECUTE  | task not currently running. |

`backEnabled` is `currentStep != SOURCE`.

### 4.3 Reset rules

When `composition.inspectVm().loadResultProperty()` transitions to a new
success result, the shell controller calls
`ConvertWizardController.resetOnSourceChange()` which: (a) calls
`ConvertController.onSourceSelected(info)` to repopulate the source + alias
lists, (b) clears `targetPath`, alias-conflict/overwrite policies revert to
defaults, `preflightReport` and `lastResult` cleared, and `currentStep`
reset to SOURCE. The wizard's selections DO NOT persist across keystores —
this matches Compliance's existing reset behavior.

## 5. Step-by-step layout

The view is a `BorderPane` with a step-list header (5 dots — current
highlighted, completed with check) and a footer (`← Back` | step title |
`Next →` for 1-4, `Convert` for 5, `Close` always available). Center is rebuilt
on `currentStep` change; each step's node is cached in
`Map<WizardStep, Node>` so navigation is cheap.

### 5.1 Step 1 — Source

Read-only card. Shows detection result (container type, encoding), source
path (or "from paste"), entry count. If `getSource() == null`, empty state
shows *"Open a keystore on the Inspect tab to populate this wizard."* and Next
is disabled.

### 5.2 Step 2 — Contents

`TableView<EntryRow>` with columns: ☑ Include | Alias | Type | Chain length |
Key algorithm | Risk status. Risk status comes from `InspectedKeyStore` (the
existing analyzed result). UNREADABLE entries render disabled (cannot include).
Footer buttons: Select all / Deselect all / Invert + an inline banner "X of Y
entries selected." Next disabled when 0 selected.

### 5.3 Step 3 — Target

Two-column grid:

* Target container: `JKS | PKCS12 | BCFKS` toggle.
* Target encoding: `Binary | Base64`.
* Target path: text field + `Browse…` (file-chooser in Save mode).
* Target store password: masked `PasswordField` with show/hide eye toggle.
  Required unless target is JKS-or-PKCS12 truststore with no private entries;
  if the user selects JKS/PKCS12 as the target but the source contains
  private-key entries, Preflight surfaces `BCFKS_TO_JKS_PRIVATE_KEY_DOWNGRADE`
  (BCFKS source) or — for any other source onto JKS — a structural downgrade
  warning that Step 4 will display.
* Base64 line width: combo (32 / 48 / 64 / 76 / 100), default 64. Disabled
  when encoding is Binary.
* Wrap with PEM-style BEGIN/END headers: checkbox, default unchecked.
  Disabled when encoding is Binary.
* Alias conflict policy: `Rename | Overwrite | Skip` combo.
* Overwrite policy: `Fail if exists | Allow overwrite | Append timestamp`
  combo.

### 5.4 Step 4 — Preflight

While `runningPreflight`: a banner *"Preflighting against profile: <NAME>…"*
and Next disabled. After completion:

1. **Blockers** (red, prominent): *"X blockers — fix or remove before
   continuing."*  If non-empty, Next is replaced by `Cannot continue — resolve
   blockers`.
2. **Warnings** (yellow, expandable).
3. **Info** (gray, collapsed).

Below the three sections, an *Entry passwords (optional)* subsection offers a
single masked `PasswordField`: "Override per-entry key password (applies to
all selected private-key entries; leave blank to be prompted per-entry at
execution time)." This field maps to `ConversionPlan.entryPasswords()` as a
parallel array of length `includedAliases().size()`. When blank, the engine's
existing `PasswordProvider.requestEntryPassword(...)` dialog prompts at
execution time inside `EntryCopy.copy(...)` — no engine change required, and
the user is never blocked here on Step 4.

### 5.5 Step 5 — Execute and verify

Before execution:

* Summary card stating the final plan in plain English (entries to write,
  target path, target format).
* Big primary `Convert` button.

After execution:

* Success panel — written bytes, target path (`Open containing folder` +
  `Copy path` buttons), 5-row verification table:
  * Source alias count vs Target alias count
  * Source certificate count vs Target certificate count
  * Fingerprint match: "Yes" / "No" (uses `ReloadVerification.fingerprints()`)
* Preflight WARN list (BLOCK findings cannot appear here because step 4
  blocked them).
* Secondary `Run Compliance assessment on the new target` button — reuses the
  existing load pipeline to populate Inspect with the new file.

## 6. Data flow and threading

### 6.1 Source loading

`composition.inspectVm().loadResultProperty()` (success only) →
`MainShellController.onKeyStoreChanged()` →
`convertController.onSourceSelected(info)` AND
`convertWizardController.resetOnSourceChange()`. One-way pipeline: Inspect
feeds Convert; Convert does not write back.

### 6.2 Step 3 → Step 4 transition

`ConvertWizardController.handleEnteredPreflight()` runs on the JavaFX
Application Thread, sets `runningPreflight = true`, and submits
`ConvertPreflightTask` to the composition's `ExecutorService`.

```java
public final class ConvertPreflightTask extends Task<PreflightReport> {
    private final ConversionPlan plan;
    private final Map<String, EntryType> sourceEntries;
    private final Profile profile;

    @Override protected PreflightReport call() throws Exception { ... }
}
```

* `setOnSucceeded` → `convertController.onPreflightProduced(report)`;
* `setOnFailed` → status bar with exception class + message; Back enabled.
* `setOnCancelled` → clear `runningPreflight`.

### 6.3 Step 5 → `ConvertTask`

`ConvertWizardController.runConvert()` builds the final `ConversionPlan`
(including the lazy-collected entry passwords) and submits `ConvertTask` (the
existing `app/task/ConvertTask.java`):

* `setOnSucceeded` → `convertVm.setLastResult(result)`; view auto-switches to
  the success panel; status bar shows "Converted X entries to <path>".
* `setOnFailed` → renders the failure card described in §7; status bar
  message kept short (no passwords).
* `setOnCancelled` → clears `runningConvert`.

### 6.4 Pattern B re-entry guard on Convert button

Mirrors the existing `currentAssessTask` pattern in `MainShellController`:

```java
private volatile ConvertTask currentConvertTask;
private volatile Button convertButton;

// in runConvert():
if (currentConvertTask != null && !currentConvertTask.isDone()) {
    setStatus("Conversion already running.");
    return;
}
// ... build task, setDisable, submit:
currentConvertTask = task;
convertButton.setDisable(true);
// terminal handlers clear currentConvertTask + setDisable(false)
```

`Back`, `Next`, and other wizard buttons remain enabled while a Convert task
is in flight (the user can leave the wizard; the task runs to completion).

### 6.5 Composition root

`AppComposition` constructs the wizard once at startup. The composition is
constructed recursively so the wizard VM can observe its siblings; the
wizard does NOT observe `inspectVm().loadResultProperty()` directly — the
shell controller routes that change through `resetOnSourceChange()`:

```java
// In AppComposition.defaultComposition() — the existing ConvertViewModel is
// replaced by the wizard VM (no separate base ConvertViewModel is needed):
ConvertWizardViewModel convertWizardVm = new ConvertWizardViewModel();
ConvertController convertController = new ConvertController(convertWizardVm);
ConvertWizardController wizardController = new ConvertWizardController(
    convertController, convertWizardVm, executor, () -> new ConvertPreflightTask(...));
ConvertView convertView = new ConvertView(convertWizardVm, wizardController);

// Accessors expose the wizard VM as the composition's convertVm() and the
// wizard controller as composition.convertWizardController().
```

Tests construct `AppComposition` via the existing `Builder` so they can
inject fake passwords / executors without touching `defaultComposition()`.

## 7. Error handling

| Failure | Surfacing | Recovery |
|---|---|---|
| Wrong source / target store password | Status bar: `LoadFailureReason`-aware message; banner on step 5 (when surfaced during execution). | User re-confirms password on step 5; re-runs Convert. |
| Missing entry password at execution | Status bar + auto-return to Step 4. | User enters missing password. |
| BLOCK preflight findings | Step 4 red block. Back enabled; Next disabled. | User adjusts entries / target format / alias policy, re-runs preflight. |
| `PreflightBlockedException` from `ConvertTask` | Status bar; wizard auto-navigates to Step 4 with the preserved `preflightReport`. | User addresses blockers. |
| Overwrite policy `FAIL_IF_EXISTS` violated | Step 5 failure card: "Target file exists. Choose a different target or change overwrite policy on Step 3." | Wizard offers "Back to Step 3". |
| Atomic-write / reload-verification failure | Step 5 failure card: short technical reason from the exception class name + message (no stack traces). Temp file cleaned up by `AtomicWriter`. | User retries or changes target. |
| Run with a SOURCE that has no entries | Step 2 banner; Next disabled. | Open a different keystore, or include at least one entry. |

### 7.1 Secrets invariants

* Status bar and Step 5 panel never include passwords.
* Fingerprint match shows only "Yes" / "No" — never the actual fingerprint.
* Step 4 entry-password prompts are masked by default; no "remember" toggle.
* Preflight warning text is bounded by what the engine emits; engine emits
  `code + alias + message` only (no key bytes per `PreflightFinding`).
* Wizard never opens the source file for write.

## 8. Testing strategy

### 8.1 Unit (no JavaFX toolkit)

* `ConvertWizardViewModelTest` — next/back blocking, derived bindings,
  `resetOnSourceChange` clearing stale state, `WizardStep` transitions.
* `ConvertWizardControllerTest` — per-step validators (table-driven),
  `buildConversionPlan(vm)` happy path + missing-field rejections,
  `runConvert` and `handleEnteredPreflight` interactions with an injectable
  `ConvertPreflightTask` factory.
* `ConvertPreflightTaskTest` — synchronous `call()` builds a plan and runs
  `Preflight.check`, surfaces a BLOCK report correctly.

### 8.2 Functional binding tests (FX toolkit, no robot)

* `ConvertViewBindingTest` — given a fully-populated VM, the bound `Node`
  graph contains step-specific widgets (target path `TextField` only on step
  3; "Preflight" labels only on step 4). Catches "view forgot to swap on
  `currentStep` change".
* `ConvertViewNavigationTest` — `next()` / `back()` mutate `currentStep`.

### 8.3 Integration

* All existing conversion engine tests stay green: `KeystoreConversionTest`,
  `PreflightTest`, `AliasResolverTest`, `AtomicWriterTest`, `EntryCopyTest`,
  `PreflightSeverityTest`. Engine is unchanged.

### 8.4 Security tests

* `convertTaskDoesNotLogPassword` — confirm `ConvertTask.call()` does not
  include the target store password in any `updateMessage`/log output.
* `convertViewStatusMessagesOmitPasswords` — confirm
  `MainShellController`'s status bar never contains password substrings on
  success or failure paths.

### 8.5 TestFX (cap per CLAUDE.md)

Exactly one TestFX test: open wizard, navigate 1→2→3, leave target path
empty, assert Back works and Next stays disabled.

## 9. Out of scope

* Private-key plaintext export (forbidden by spec — wizard never offers it).
* HTML/Markdown conversion-report renderers. JSON conversion reports are also
  out of scope (the success card is the report).
* Concurrent runs of multiple ConvertTasks (re-entry guard handles re-click,
  not multi-wizard).
* Source pickers inside the wizard.
* A second, wizard-local profile selector.

## 10. Open risks

* **`ConvertTask` re-entry guard pattern B** — same shape as Compliance's
  `currentAssessTask` guard; follows the established convention.
* **`ConvertPreflightTask` test seam** — production takes an
  `ExecutorService` and a `ConvertPreflightTask` factory; tests inject a
  synchronous factory to avoid `Platform.startup` and verify validator wiring.
* **Step 2 risk status from `InspectedKeyStore`** — the `InspectedKeyStore` is
  computed by the analyze task. If the user navigates to Convert BEFORE
  analyze completes (rare on a fresh load — they happen together), Step 2
  shows entries with a placeholder risk and Next still works; analyze
  completion triggers a re-render.
