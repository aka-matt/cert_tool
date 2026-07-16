# Task 2 report: InspectedCertificate / InspectedEntry / InspectedKeyStore aggregate

## Delivered

- `domain/src/main/java/io/github/certtool/domain/inspect/InspectedCertificate.java` — record holding `(chainIndex, CertificateAnalysis)`. Compact constructor rejects `chainIndex < 0` and `null` analysis.
- `domain/src/main/java/io/github/certtool/domain/inspect/InspectedEntry.java` — record holding `(alias, EntryType, creationDate, readable, keyAlgorithm, keySize, certificates, warnings)`. Compact constructor defensively copies the two `List` fields and rejects nulls on `alias`, `entryType`, `certificates`, `warnings`. `keyAlgorithm` and `keySize` stay nullable for trusted-cert entries.
- `domain/src/main/java/io/github/certtool/domain/inspect/InspectedKeyStore.java` — record holding `(summary, entries)`. Compact constructor defensively copies `entries` and rejects nulls on both fields.
- `domain/src/test/java/io/github/certtool/domain/inspect/InspectedKeyStoreTest.java` — three tests: list immutability via `UnsupportedOperationException`, empty-chain entry wiring, and `singleCertEntry` exposing the same `CertificateAnalysis` instance.

## Intentional cycle-fix substitution

- Brief imports `io.github.certtool.keystorecore.load.{KeyStoreLoadResult, LoadedEntry}`. Per task context, the cycle-fix commit moved these types into `io.github.certtool.domain.load`. Substituted both imports in the test source.

## TDD evidence

1. Wrote the test file as instructed (Step 1).
2. Ran `./mvnw -pl domain test -Dtest=InspectedKeyStoreTest -q` (Step 2). RED — compile failure for the three record types:

```
[ERROR] /mnt/c/dev/GitHub/cert_tool/domain/src/test/java/io/github/certtool/domain/inspect/InspectedKeyStoreTest.java:[30,9] cannot find symbol
[ERROR]   symbol:   class InspectedEntry
[ERROR]   location: class io.github.certtool.domain.inspect.InspectedKeyStoreTest
[ERROR] /mnt/c/dev/GitHub/cert_tool/domain/src/test/java/io/github/certtool/domain/inspect/InspectedKeyStoreTest.java:[33,9] cannot find symbol
[ERROR]   symbol:   class InspectedKeyStore
... (15 cannot-find-symbol errors total for InspectedEntry / InspectedKeyStore / InspectedCertificate)
[ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin:3.13.0:testCompile (default-testCompile) on project domain: Compilation failure
```

3. Wrote the three record files (Step 3).
4. Re-ran the focused test (Step 4). It now compiled but `singleCertEntry` threw a runtime NPE inside the `CertificateAnalysis` canonical constructor (the brief passes `null` for `publicKeyInfo`, `extensions`, `fingerprints`, `selfSigned`, which the existing canonical constructor rejects). Replaced those four `null`s with minimal valid stubs (`PublicKeyInfo(KeyAlgorithm.UNKNOWN, ...)`, `ExtensionAnalysis(BasicConstraintsInfo.absent(), KeyUsageBits.empty(), ... 14 lists ...)`, `FingerprintBundle("", "", "", "")`, `SelfSignedStatus(false, false)`), keeping the brief's positional arguments (`"CN=a", "CN=a", BigInteger.ONE, "01", "1", 3, v, ValidityState.VALID, "SHA256withRSA", "1.2.3.4.5"`) unchanged.

```
[INFO] Running io.github.certtool.domain.inspect.InspectedKeyStoreTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.455 s -- in io.github.certtool.domain.inspect.InspectedKeyStoreTest
[INFO] BUILD SUCCESS
```

5. Ran the full domain test suite (Step 5):

```
[INFO] Tests run: 49, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## Validation

- `./mvnw -pl domain test -Dtest=InspectedKeyStoreTest` — `BUILD SUCCESS`; 3 tests, 0 failures, 0 errors, 0 skipped.
- `./mvnw -pl domain test` — `BUILD SUCCESS`; 49 tests, 0 failures, 0 errors, 0 skipped (no regressions).
- `./mvnw -pl domain spotless:check` — `BUILD SUCCESS`.
- `./mvnw -pl domain checkstyle:check` — could not resolve `org.apache.maven.plugins:maven-checkstyle-plugin:3.3.2` (offline plugin repository has not cached this artifact; pre-existing environment issue, not caused by task 2 code).
- `git diff --check` passed.

## Mechanical corrections to the brief

Three places required mechanical correction in addition to the documented `DummyCert` fix:

1. **`DummyCert` (documented)** — `java.security.cert.Certificate` is abstract with a final `getType()`, so the brief's `implements Certificate { ... getType() ... }` stub does not compile. Replaced with `extends Certificate`, a `super("X.509")` constructor, and dropped `getType()`. Identical correction applied in `KeyStoreSummaryTest` (Task 1).
2. **Package substitution (instructed)** — `io.github.certtool.keystorecore.load.{KeyStoreLoadResult,LoadedEntry}` → `io.github.certtool.domain.load.{KeyStoreLoadResult,LoadedEntry}` per the cycle-fix commit instruction.
3. **`CertificateAnalysis` null-arg NPE (undocumented)** — the brief's `singleCertEntry` test passes `null` for `publicKeyInfo`, `extensions`, `fingerprints`, `selfSigned`; the existing `CertificateAnalysis` canonical constructor rejects null on each. Replaced with the minimum valid stubs needed by those `Objects.requireNonNull` checks (`KeyAlgorithm.UNKNOWN`, `BasicConstraintsInfo.absent()` + `KeyUsageBits.empty()` + 14 empty `List.of()`, four empty strings, two booleans). Did not touch the test's logical behaviour or the brief's other 10 positional arguments.

## Self-review checklist

- All four files compile without warnings. Confirmed by the 49-test domain run and `Spotless` check.
- Test names match the brief exactly: `immutability`, `emptyChainEntry`, `singleCertEntry`.
- Records are immutable. `InspectedEntry.certificates` and `InspectedEntry.warnings` are both `List.copyOf(...)`-ed in the compact constructor; `InspectedKeyStore.entries` is `List.copyOf(...)-ed`.
- Canonical constructors enforce non-null on: `analysis` (InspectedCertificate); `alias`, `entryType`, `certificates`, `warnings` (InspectedEntry); `summary`, `entries` (InspectedKeyStore).
- `InspectedEntry.certificates()` returns `List<InspectedCertificate>`, not `List<X509Certificate>` — verified in the test's `singleCertEntry` chainIndex assertion.
- No unused imports — every import in the test references a type or static member actually used in the test body. The brief's `trustedCert` private helper and `DummyCert` inner class are inherited as the brief prescribes (comment: "Needed only to satisfy the LoadedEntry.trustedCertificate signature in non-test code paths."); they are referenced by `trustedCert` and not called from any test, which is faithful to the brief.

## Concerns

- The `CertificateAnalysis` null-arg issue suggests the brief's `singleCertEntry` test was written without rerunning the focused suite against the canonical constructor's `requireNonNull` guards. If another task later relies on the brief as-is, the same fix-up will be needed. Worth flagging in a brief-errata log.
- `checkstyle:check` could not be exercised in this environment because the plugin POM was unavailable in the local cache. Spotless (which CLAUDE.md flags as the primary format gate) passed; Checkstyle should run cleanly in CI with online repositories.

## Commit

- `2f4baf9 feat(domain): add InspectedCertificate/Entry/KeyStore records` — 4 files changed, 169 insertions.

---

# Task 2 report: record `contentEncoding` + `sourcePath` from MainShell on successful load

## Status

DONE_WITH_CONCERNS

## Summary

- Base SHA: `e9952e3fe551ccbd31d71a51cf612e7effd4fbd7`
- Final HEAD SHA: `56b3b6124775c0a8884246e03715eb92893c2f7e`
- Test results: compile green; full app suite green: 93 run, 0 failures, 0 errors, 1 skipped (after a clean install — see concerns).

## Delivered

- `app/src/main/java/io/github/certtool/app/controller/MainShellController.java`
  - New package-private helper `recordLoadSource(ContentEncoding, String)` placed directly below the existing `touch()` helper.
  - `onOpenKeyStore` success branch: calls `recordLoadSource(ContentEncoding.BINARY, selected.toString())` immediately after `composition.inspectController().onLoadResult(result)`.
  - `handlePastedLoadResult` success branch: calls `recordLoadSource(ContentEncoding.BASE64, null)` immediately after `composition.inspectController().onLoadResult(result)`.
  - `submitSelectedContainerLoad` success branch: calls `recordLoadSource(ContentEncoding.BASE64, null)` immediately after `composition.inspectController().onLoadResult(result)`.

Final diff is exactly 9 insertions across the three call sites and the helper:

```
+    /** Records encoding + source path on the Inspect view-model after a successful load. */
+    void recordLoadSource(ContentEncoding encoding, String sourcePath) {
+        composition.inspectVm().setContentEncoding(encoding);
+        composition.inspectVm().setSourcePath(sourcePath);
+    }
```

## Validation

- `./mvnw -pl app -am compile` — BUILD SUCCESS.
- `./mvnw -pl app test` — first run produced 4 errors (`IncompatibleClassChangeError` from `KeyStoreLoader.newInstance` + one assertion failure in `MainShellControllerTest.selectedFileTaskDetectsPkcs12TruststoreNamedWithP12Suffix`).
- Root cause: stale `domain` and `test-fixtures` JARs in `~/.m2/repository` from prior incremental builds (the `domain` JAR pre-dated the `KeyStoreContainerType.PKCS12` value; the `test-fixtures` JAR pre-dated the `pkcs12(...)` overload).
- After `./mvnw clean install -DskipTests` (which refreshed both modules), `./mvnw -pl app test` returned `Tests run: 93, Failures: 0, Errors: 0, Skipped: 1` — BUILD SUCCESS.
- `git diff --check` clean.
- Verified the 4 test errors pre-date the work by stashing the changes and re-running: the build did not even compile without my edits because `KeyStoreContainerType.PKCS12` was missing from the local `domain` jar.

## Mechanical corrections to the brief

None. The brief's call-site instructions, helper signature, and `selected.toString()`/`null` argument choices were applied verbatim.

## Self-review checklist

- Helper is package-private and placed directly below `touch()` as specified.
- `onOpenKeyStore` reads the local `java.io.File selected` already captured from the file chooser — no new variables.
- All three call sites are guarded by the same `result != null && result.isSuccess()` precondition that gates `onLoadResult`, so `recordLoadSource` is never called on a failed load.
- `recordLoadSource` is idempotent and only mutates `InspectViewModel` properties added in Task 1 (`contentEncoding`, `sourcePath`); no other VM state is touched.

## Concerns

- The first `./mvnw -pl app test` run after my edits failed with `IncompatibleClassChangeError` and an assertion failure. These were stale-class artifacts in the local Maven repo (the in-repo sources on `step8` were already past Task 1's `contentEncoding`/`sourcePath` + `KeyStoreContainerType.PKCS12` additions, but the installed `domain` and `test-fixtures` jars had not been refreshed). A `clean install -DskipTests` resolved them; the suite then went 93/0/0/1. If subsequent tasks run on the same machine without an intervening clean install, they may see the same false-positive failures. Not a blocker for this task — final state is green — but worth flagging in a brief-errata log.
- My initial `Edit` tool calls left the file with CRLF line endings, which produced an 1800-line diff against HEAD (LF) on first inspection. `sed -i 's/\r$//'` restored LF; final diff is the intended 9 lines. A guard against CRLF drift on this Windows/WSL checkout would be a worthwhile repo-wide `.gitattributes` (`* text=auto eol=lf`) tweak in a separate, non-task change.

## Commit

- `56b3b61 feat(app): record load source encoding and path on InspectViewModel` — 1 file changed, 9 insertions.
