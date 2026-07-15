# Task 2 Report: File-open workflow and truststore UI copy

## Implementation

- Added `AppComposition.autoDetectLoadTask(byte[])`, which supplies the configured loader and current password provider to `AutoDetectKeyStoreLoadTask`.
- Changed file-open loading in `MainShellController` to use that factory, eliminating filename-extension container selection.
- Updated the menu label, chooser title, and filters to make JKS and BCFKS truststores discoverable, including `*.truststore` and an all-files fallback.
- Added a composition-path regression test that creates a real BCFKS trusted-certificate store with an empty password (matching the test composition password provider), executes the real task, and asserts a `BCFKS` result.

The task's `call()` method is protected, as required by the JavaFX `Task` override in Task 1, so the controller-package test synchronously invokes `run()` and reads `get()` rather than widening Task 1's API. It exercises the same production task behavior.

## Files changed

- `app/src/main/java/io/github/certtool/app/AppComposition.java`
- `app/src/main/java/io/github/certtool/app/controller/MainShellController.java`
- `app/src/test/java/io/github/certtool/app/controller/MainShellControllerTest.java`

## TDD evidence

### RED

1. Added `compositionAutoDetectsBcfksTruststore`, before adding the factory.
2. Required command attempted:

   ```bash
   /opt/homebrew/bin/bash ./mvnw test -pl app -am -Dtest=MainShellControllerTest
   ```

   It exited non-zero before the `app` module because upstream reactor modules have no matching `MainShellControllerTest`; Surefire reports `No tests matching pattern "MainShellControllerTest" were executed!` in `domain`.
3. Re-ran with Maven's standard selector override so the reactor reached `app`:

   ```bash
   /opt/homebrew/bin/bash ./mvnw test -pl app -am -Dtest=MainShellControllerTest -Dsurefire.failIfNoSpecifiedTests=false
   ```

   This failed at `MainShellControllerTest.java:[89,56]` with the expected missing `AppComposition.autoDetectLoadTask(byte[])` symbol.

### GREEN

After the minimal wiring, the same override command initially exposed that `AutoDetectKeyStoreLoadTask.call()` is protected. The test was adjusted to use `run()` plus `get()` without changing Task 1. The focused command then succeeded:

```bash
/opt/homebrew/bin/bash ./mvnw test -pl app -am -Dtest=MainShellControllerTest -Dsurefire.failIfNoSpecifiedTests=false
```

Result: `MainShellControllerTest`: 14 tests, 0 failures, 0 errors, 0 skipped; reactor `BUILD SUCCESS`.

## Verification

```bash
/opt/homebrew/bin/bash ./mvnw spotless:apply -pl app
git diff --check
/opt/homebrew/bin/bash ./mvnw verify
```

Results:

- Spotless completed successfully; no files outside the three scoped Task 2 files were modified.
- `git diff --check` completed successfully.
- Full `verify` completed successfully. Every module succeeded; app ran 88 tests with 0 failures and 0 errors (1 skipped).

## Self-review

- The file-open path retains its existing success listener, inspection handoff, background-executor submission, and `ContentEncoding.BINARY` analysis.
- No extension-derived container choice remains in `onOpenKeyStore()`.
- The composition factory reads `activePasswordProvider` at task construction time, consistent with the existing task factories.
- Test data contains a generated certificate and empty test password only; no real keystore, certificate, or secret was added.

## Concerns

- The exact focused Maven command in the brief cannot reach `app` in this multi-module reactor unless `-Dsurefire.failIfNoSpecifiedTests=false` is appended; this is a Maven test-selection configuration behavior, not a code issue.
- There was an existing untracked user file, `docs/superpowers/plans/2026-07-15-truststore-file-support.md`; it was preserved and excluded from the commit.

---

# Follow-up fix: background selected-file reading

## Root cause and fix

`MainShellController.onOpenKeyStore()` previously invoked `Files.readAllBytes(path)` after the
chooser returned, before creating and submitting `AutoDetectKeyStoreLoadTask`. Because chooser
actions run on the JavaFX Application Thread, a large or slow selected file could block the UI.

- Added `AutoDetectKeyStoreLoadTask(KeyStoreLoader, Path, PasswordProvider)`. Its `call()` method
  reads the path and then runs the existing content-based auto-detection loader.
- Preserved the existing byte-array constructor and `AppComposition.autoDetectLoadTask(byte[])` for
  existing Task 1/Task 2 callers; added `AppComposition.autoDetectLoadTask(Path)` for selected
  files.
- Changed `onOpenKeyStore()` to create, bind, and submit the path-backed task without performing
  any file read on the JavaFX Application Thread.
- Mapped `NoSuchFileException` to typed `FILE_NOT_FOUND` and other `IOException`/
  `SecurityException` read failures to typed `FILE_NOT_READABLE`. Generic task and visible status
  messages contain no selected path or secret data.
- Replaced file-name-bearing success status text with generic keystore/truststore status text.

## Regression test and TDD evidence

Added `selectedFileTaskReadsBcfksTruststoreBytesDuringTaskExecution` to
`MainShellControllerTest`. It writes a generated BCFKS trusted-certificate store to a temporary
file, constructs the new path-based composition task, executes `Task.run()`, and asserts that
`task.get().container()` is `BCFKS`. This proves selected-file bytes are read in the task execution
path rather than in the chooser handler.

### RED

After adding the test, ran:

```bash
/opt/homebrew/bin/bash ./mvnw test -pl app -am -Dtest=MainShellControllerTest -Dsurefire.failIfNoSpecifiedTests=false
```

Result: reactor reached `app` and failed compilation at
`MainShellControllerTest.java:[110,80]`: `Path cannot be converted to byte[]`, proving the
path-based factory/task behavior did not yet exist.

### GREEN

After the focused implementation, reran the same command:

```bash
/opt/homebrew/bin/bash ./mvnw test -pl app -am -Dtest=MainShellControllerTest -Dsurefire.failIfNoSpecifiedTests=false
```

Result: `BUILD SUCCESS`; `MainShellControllerTest` ran 15 tests with 0 failures, 0 errors, and 0
skipped.

## Final verification

```bash
/opt/homebrew/bin/bash ./mvnw spotless:apply -pl app
git diff --check
/opt/homebrew/bin/bash ./mvnw verify
```

Results: Spotless and `git diff --check` succeeded. Full `verify` succeeded for every module; the
app module ran 89 tests with 0 failures, 0 errors, and 1 skipped.

## Follow-up concerns

- Maven emitted pre-existing model/deprecation/native-access warnings during verification; the
  reactor still completed with `BUILD SUCCESS`.
- The pre-existing untracked plan file remains excluded from the commit.
