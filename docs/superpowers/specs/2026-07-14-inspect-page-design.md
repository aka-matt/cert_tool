# Inspect Page Detailed View Design

## Goal

After loading a JKS or BCFKS keystore (binary or base64), the Inspect page renders a full, immediately visible summary of the keystore itself and every certificate inside it. The page no longer shows just a single "select alias" label. Selecting an alias from the left navigation reveals per-entry chain, extensions, and PEM details in a tabbed view, while the Overview tab keeps the whole-keystore picture available.

## Scope

This change touches only the Inspect view and its supporting view-model and tasks. The Compliance Inspector (right pane), the Convert wizard, and the Runtime page are untouched. The right pane reserves a stub slot for the future Compliance Inspector — no Compliance logic is implemented here. Per CLAUDE.md §18 the right-pane integration is deferred.

## Module Architecture

New aggregate value objects live in a dedicated `domain/inspect` package and are pure (no JavaFX, no `KeyStore`/`CertificateFactory` imports).

| Module | Type | Responsibility |
| --- | --- | --- |
| `domain/inspect/` | `KeyStoreSummary` record | Container type, provider name/version, source encoding, entry counts by group, total chain length, total bytes |
| `domain/inspect/` | `CertificateRef` record | Pointer to one certificate within one entry — chain index + alias + the underlying `java.security.cert.X509Certificate` |
| `domain/inspect/` | `InspectedEntry` record | One loaded entry plus its parsed chain analysis (list of `CertificateAnalysis`) and any per-entry warnings |
| `domain/inspect/` | `InspectedKeyStore` record | Aggregate wrapping `KeyStoreSummary` + immutable `List<InspectedEntry>` |
| `app/task/` | `AnalyzeKeyStoreTask` | Background `Task<InspectedKeyStore>` driving `CertificateAnalyzer` and `ChainAnalyzer` |
| `app/viewmodel/InspectViewModel` | (extended) | Exposes `inspectedProperty`, `currentEntryProperty`, `currentCertificateIndexProperty` |
| `app/controller/InspectController` | (extended) | New `onLoadResult` no-op when null; new `applyInspection` for the background task result |
| `app/controller/MainShellController` | (extended) | `buildInspectView` is rewritten to assemble the BorderPane + TreeView + TabPane |
| `app/AppComposition` | (extended) | Factory for `AnalyzeKeyStoreTask` |

The X509 parsing work runs on the same `backgroundExecutor` already wired up by `AppComposition`. The UI is updated on the JavaFX Application Thread via the existing `setOnSucceeded` callback.

## Data Flow

1. `LoadKeyStoreTask` succeeds.
2. `MainShellController.onOpenKeyStore.setOnSucceeded` keeps the existing call to `inspectController.onLoadResult(result)` (which sets `loadResultProperty` and rebuilds the left nav).
3. Same callback submits a new `AnalyzeKeyStoreTask(result)` to the background executor. The current `progress` bar reflects parsing progress.
4. The task walks every entry's certificate chain, running `CertificateAnalyzer.analyze(X509Certificate)` on each cert. Per-entry failures are caught and recorded into `InspectedEntry.warnings` — one bad cert does not abort the rest.
5. On success, `inspectController.applyInspection(inspected)` calls `viewModel.setInspected(inspected)`.
6. The view binds to:
   - `KeyStoreSummary` → Overview's keystore header card.
   - `inspected.entries` (1-to-1 with `navNodes`) → Overview's list of per-entry cards.
   - `selectedAliasProperty` → drives `currentEntryProperty` and `currentCertificateIndexProperty`.
   - Each tab's content is rebuilt from `currentEntry().certificates().get(currentCertificateIndex())`.

The default selected alias is the first alias in the first non-empty group, exactly as the existing `InspectViewModel` already does. The Overview tab is shown by default.

## Background Task

`AnalyzeKeyStoreTask extends Task<InspectedKeyStore>`:

- Input: `KeyStoreLoadResult`
- Steps:
  1. Build `KeyStoreSummary.from(result)`.
  2. For each `LoadedEntry`: collect every `X509Certificate` in the chain. For each cert run `CertificateAnalyzer.analyze(cert)` inside a try/catch that pushes an `InspectedCertificateAnalysis` with `warning` on failure, or the parsed `CertificateAnalysis` otherwise.
  3. `updateProgress(done, total)` and respect `isCancelled()`.
  4. Return `new InspectedKeyStore(summary, entries)`.
- Cancellation: returns no result; controller observes `Worker.State.CANCELLED` and does not call `setInspected`.

The task reuses `progress` binding through `task.stateProperty()` and `task.progressProperty()`.

## View Layout

`MainShellController.buildInspectView()` returns a `BorderPane`:

- `center`: a `SplitPane` (horizontal, divider at 25 %):
  - `left`: a `TreeView<String>` whose root has four groups (`Private Keys`, `Trusted Certificates`, `Secret Keys`, `Unreadable Entries`), each containing leaf nodes for matching aliases. TreeView selection writes back to `InspectViewModel.selectedAliasProperty()`.
  - `right`: a `TabPane` with five tabs:
    - **Overview** — keystore header (container, provider, version, entry counts, source path if any) plus a vertical `ListView<InspectedEntry>` where each row is a `TitledPane` ("[type] alias — chain length N") opening to show key algorithm/size, earliest expiry, chain root subject, self-signed flag.
    - **Certificate** — selected entry's first certificate by default. A `GridPane` showing subject, issuer, serial (hex + dec), version, validity window, signature algorithm + OID, public key info, fingerprints (SHA-256 + SHA-1, formatted + raw), self-signed status. Two buttons `◀ Prev` / `Next ▶` move `currentCertificateIndexProperty`.
    - **Chain** — header listing chain length. One `HBox` row per certificate with subject, issuer, validity, and a `[self-signed]` chip when applicable. A "Verify chain" button (no-op stub for now that just shows a placeholder dialog).
    - **Extensions** — Basic Constraints, Key Usage bits, EKU OIDs, SAN (with type labels), IAN, SKI, AKI, Certificate Policies, CRL DPs, AIA. Unrecognized critical extensions shown at the top with a warning icon.
    - **PEM** — monospace `TextArea` rendering the selected certificate's PEM with a Copy button (writes to `Clipboard` via `ClipboardContent`, never logs the PEM).

- `right` (BorderPane.right): a 0-width placeholder region labelled "Compliance Inspector (deferred)". Collapsed by default; will be wired later.

The whole right side becomes visible the moment `loadResultProperty` is non-null. Before any keystore is loaded, both columns show the existing "Open a KeyStore to inspect." placeholder.

## Security Constraints

Following CLAUDE.md §2:

- No log message, status text, error dialog, PEM view, or report may include the store password, key password, private key bytes, secret key bytes, full Base64 input, or full keystore binary. `CertificateAnalyzer` already meets this contract; we re-verify in tests.
- The PEM view is a re-encoding of a public certificate — explicitly safe to display.
- The `progress` bar text only ever shows counts (e.g. "Analyzing certificates 3/12"). No entry aliases go to logs.
- No new temp files on disk; all analysis is in-memory.

## Test Strategy

Strict TDD per CLAUDE.md. Tests are written before the corresponding production code. We avoid TestFX entirely for this work (UI rendering verification stays at unit level).

1. `domain/src/test/java/io/github/certtool/domain/inspect/KeyStoreSummaryTest.java`
   - Construction from a `KeyStoreLoadResult` with mixed entry types.
   - Empty load result produces zero counts.
   - Counts each group correctly.

2. `domain/src/test/java/io/github/certtool/domain/inspect/InspectedKeyStoreTest.java`
   - Record immutability: caller cannot mutate the entries list.
   - `InspectedEntry` with empty chain produces a single-element list (the warning) rather than a `null` cert list.

3. `app/src/test/java/io/github/certtool/app/task/AnalyzeKeyStoreTaskTest.java`
   - Given a fake `KeyStoreLoadResult` carrying one self-signed cert and one trusted cert, the task completes with both entries parsed and a populated `KeyStoreSummary`.
   - Cancellation: after `task.cancel()`, `task.get()` throws `CancellationException`; `getValue()` returns null; `stateProperty()` reaches `CANCELLED`.
   - Per-cert failure: a deliberately broken `X509Certificate` triggers a try/catch path that records a warning and lets parsing continue.

4. `app/src/test/java/io/github/certtool/app/viewmodel/InspectViewModelTest.java` (extend existing)
   - `setInspected` with non-empty entries auto-selects the first alias.
   - `currentEntryProperty` tracks `selectedAliasProperty` when the alias matches an inspected entry.
   - `currentCertificateIndexProperty` resets to 0 after switching alias.

5. `app/src/test/java/io/github/certtool/app/controller/MainShellControllerTest.java` (extend existing)
   - `buildInspectView()` returns a non-null `Node`.
   - When `InspectViewModel` has a populated `inspectedProperty`, the Overview tab contains text from `KeyStoreSummary`.

6. `app/src/test/java/io/github/certtool/app/controller/InspectControllerTest.java`
   - `applyInspection(null)` is a no-op (and does not crash).
   - Calling `applyInspection` delegates to `viewModel.setInspected`.

7. `app/src/test/java/io/github/certtool/app/task/AnalyzeKeyStoreTaskSecurityTest.java`
   - Captures the Logback output of a single run and asserts no private-key bytes, no password material, no full Base64, no full keystore binary appears in any log event.

No `TestFX` UI test is added; UI assertions live in the VM unit tests so rendering remains a thin binding layer.

## Risks and Mitigations

| Risk | Mitigation |
| --- | --- |
| Large keystore with many certificates makes the parsing pass slow | Task reports progress and is cancellable; the status bar's existing `progress` widget reuses the new progress events. |
| A malformed certificate throws inside `CertificateAnalyzer.analyze` | Per-certificate `try/catch` records a warning and continues; `InspectedEntry.warnings` is shown in Overview. |
| `setInspected` invoked off the JavaFX Application Thread | Submit through `setOnSucceeded` so it always lands on the FX thread. |
| Many extensions on a busy cert make the Certificate tab too tall | Wrap the Certificate tab in a `ScrollPane`. Tabs share the same scroll policy. |
| Users paste a private-key entry and expect the secret to show | Private key bits never enter the model — no private key data is exposed, matches CLAUDE.md §2. |

## File List

New:

- `domain/src/main/java/io/github/certtool/domain/inspect/KeyStoreSummary.java`
- `domain/src/main/java/io/github/certtool/domain/inspect/InspectedEntry.java`
- `domain/src/main/java/io/github/certtool/domain/inspect/InspectedCertificate.java`
- `domain/src/main/java/io/github/certtool/domain/inspect/InspectedKeyStore.java`
- `app/src/main/java/io/github/certtool/app/task/AnalyzeKeyStoreTask.java`

New tests:

- `domain/src/test/java/io/github/certtool/domain/inspect/KeyStoreSummaryTest.java`
- `domain/src/test/java/io/github/certtool/domain/inspect/InspectedKeyStoreTest.java`
- `app/src/test/java/io/github/certtool/app/task/AnalyzeKeyStoreTaskTest.java`
- `app/src/test/java/io/github/certtool/app/task/AnalyzeKeyStoreTaskSecurityTest.java`
- `app/src/test/java/io/github/certtool/app/controller/InspectControllerTest.java`

Modified:

- `app/src/main/java/io/github/certtool/app/AppComposition.java` — register a new `analyzeTask(...)` factory.
- `app/src/main/java/io/github/certtool/app/controller/MainShellController.java` — fully rewrite `buildInspectView()`.
- `app/src/main/java/io/github/certtool/app/controller/InspectController.java` — extend with `applyInspection`.
- `app/src/main/java/io/github/certtool/app/viewmodel/InspectViewModel.java` — add `inspectedProperty`, `currentEntryProperty`, `currentCertificateIndexProperty`.

Modified tests:

- `app/src/test/java/io/github/certtool/app/viewmodel/InspectViewModelTest.java` — extended to cover the new VM properties.
- `app/src/test/java/io/github/certtool/app/controller/MainShellControllerTest.java` — extended to assert the inspect view is non-null and binds to the Overview tab content.

## Completion Criteria

Matches CLAUDE.md's "Completion Criteria":

- Both JKS and BCFKS loadable in Binary and Base64 forms.
- Loaded keystore immediately shows the keystore summary plus one card per entry without requiring any alias click.
- Selecting an alias reveals the Certificate, Chain, Extensions, and PEM tabs.
- `CertificateAnalyzer` output populates every known field, including fingerprints, SKI/AKI, SANs, Key Usage, Basic Constraints, EKU, CRL DPs, and AIA.
- No password, no private key, no Base64 blob reaches logs, status, or PEM view.
- Background task is cancellable and reports progress to the existing status bar.
- `./mvnw verify` passes (Spotless, Checkstyle, JaCoCo coverage, OWASP Dependency-Check, CycloneDX SBOM).
- No new TestFX UI test is added.
