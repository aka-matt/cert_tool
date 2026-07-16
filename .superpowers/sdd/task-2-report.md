# Task 2 Report — ConvertWizardController

## Status
DONE_WITH_CONCERNS

## Commit SHA
- Pre-SHA: 8b1277dc8a45674c6e976cbd5ef6444b05cc43ab
- Post-SHA: fc9e4eea9ddf02704669d9f4c01f633cb4a0a744

## One-line test summary
7/7 ConvertWizardControllerTest pass; full app suite 117/117 pass (1 pre-existing ThemeSwitchSmokeTest skip).

## Validation
- `./mvnw -pl app test -Dtest=ConvertWizardControllerTest -Dsurefire.failIfNoSpecifiedTests=false` — RED: compile error "cannot find symbol class ConvertWizardController" (expected, pre-implementation).
- After implementation: same command → `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0` → BUILD SUCCESS.
- `./mvnw -pl app spotless:apply` — BUILD SUCCESS (no formatting changes needed after dropping unused imports/LOG).
- `./mvnw -pl app test` — `Tests run: 117, Failures: 0, Errors: 0, Skipped: 1` → BUILD SUCCESS.

## Mechanical notes
- Brief's `LoadedKeyStoreInfo` 5-arg constructor is stale — current record has 6 fields (`containerType, encoding, sourcePath, sizeBytes, integrityCheckPassed, aliases`). The test's `seedSource()` helper was corrected to pass `1024L, true` as the missing args.
- Brief's `vm.getSource().contentEncoding()` is stale — current accessor is `encoding()`. Replaced with `vm.getSource().encoding()`.
- Brief's `vm.getSource().storePassword()` does NOT exist on `LoadedKeyStoreInfo` (no source-store-password field). Substituted with a literal `new char[0]` for `sourceStorePassword`, with a comment noting Task 8 will add the proper plumbing. The early-throw (`vm.getSource() == null`) guards the only test that hits this code path, so the test still passes; but the controller MUST compile, hence the substitution.
- Dropped the brief's `HashMap` import (unused in the final code per Spotless discipline).
- Dropped the unused `LOG` SLF4J logger and its imports — the controller has no logging calls in this task (the background-task submission paths run in `handleEnteredPreflight` / `runConvert`, which are no-ops until Tasks 4 and 12 wire the factories).
- Dropped the now-unused `cloneOrEmpty(char[])` helper (it was only called from the substituted-out storePassword line).
- The `convertController` constructor parameter is held but not used in this task's body — it will be used by future wiring (Task 4).

## Concerns (for reviewer)
1. **`storePassword()` substitution.** The brief's `buildConversionPlan` references `vm.getSource().storePassword()`, which doesn't exist. I substituted `new char[0]` to keep the file compiling AND keep the test green (the test calls buildConversionPlan with `source == null`, so the early-throw fires before any storePassword deref). The substitution is documented in a comment. **Reviewer should flag this as something Task 8 must resolve** by either adding `storePassword()` to `LoadedKeyStoreInfo` (and a PasswordProvider carrier) or by changing the ConversionPlan constructor to accept a PasswordProvider reference.
2. **Test surface is intentionally narrow.** Only 7 tests, all unit-level, no FX toolkit. None of `handleEnteredPreflight` / `runConvert` is exercised (they're no-ops until Tasks 4 and 12 wire the factories). This matches the brief, but reviewer should know the production paths are untested here.
3. **`resetOnSourceChange` doesn't call `convertController.onSourceSelected(null)`.** The brief just clears VM state; the `convertController` field is held but unused in this task. If the production wiring (Task 5+) expects `resetOnSourceChange` to also call `convertController.onSourceSelected(null)` to wipe aliases, that's a Task 5 concern — but worth reviewer attention now.
4. **`convertController` field held unused in this task.** Checkstyle's "UnusedFields" rule may flag this. I left it in because the brief's constructor signature requires it and Task 4 will use it; but if reviewer prefers a `@SuppressWarnings` annotation or a removal-and-re-add approach, that's an option.

## Files changed
- Created: `app/src/main/java/io/github/certtool/app/controller/ConvertWizardController.java`
- Created: `app/src/test/java/io/github/certtool/app/controller/ConvertWizardControllerTest.java`

---

# Older task reports follow below (not from this turn)