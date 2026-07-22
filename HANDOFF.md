# HANDOFF — AutoJs6 Fleet/Headless Profile System

## Project Overview

**AutoJs6 v6.7.0-fleet-profile** (Android automation app, Rhino JavaScript engine).
- Min SDK 24, Compile/Target SDK 36
- Build: Gradle KTS with IDE-aware AGP/Kotlin/KSP auto-detection
- Fork: `github.com/djbclark/AutoJs6` (remote: `fork`)
- Upstream: `github.com/SuperMonster003/AutoJs6` (remote: `origin`) — no new commits since fork

## Full Commit History (most recent first)

| Commit | Description |
|--------|-------------|
| `3877e0fa` | **Tests** FleetProfileApplierTest (18 tests) + HANDOFF.md update |
| `0d0f76a3` | **Fix** versionName includes branch qualifier (`6.7.0-fleet-profile` matches tag) |
| `67afc538` | **Feat** Unix-style log format with INFO/WARN/ERROR levels, daily rotation |
| `248fef4d` | **Fix** code review cleanup — unused imports, deprecation, duplication |
| `369a30d0` | **Feat** replace Toast with daily rotating log file (silent, no UI) |
| `46a26e37` | Docs: comprehensive HANDOFF.md update |
| `fa001ad7` | Bump FLEET_BUILD_NUMBER to 5 |
| `bcbf4390` | **Fix** drawer subtitle crash (ArrayIndexOutOfBoundsException bounds check) |
| `e38b5a2a` | Docs: subtitle crash bug analysis |
| `34e2d8a0` | Bump FLEET_BUILD_NUMBER to 4 |
| `95f0d3f0` | **Feat** broadcast + file output for FleetProfileActivity result |
| `92a39d5c` | **Feat** fleet build label in APK names |
| `622ba719` | **Feat** result intent extras for FleetProfileActivity |
| `5668369b` | Docs: add HANDOFF.md |
| `453ac8f5` | **Fix** human-readable aliases for list preferences |
| `9ad9f2cb` | **Fix** console color resources in bottom_sheet_log |
| `b887e79a` | **Fix** FleetProfileApplier key prefix + LogBottomSheet AutoJs access |
| `3e955b3e` | **Feat** fleet/headless configuration profile support (PR #553) |

Working tree clean, no stashes. 17 commits ahead of origin/master.

## Key Source Files

| File | Purpose |
|------|---------|
| `app/src/main/java/.../core/pref/fleet/FleetProfileApplier.kt` | Core logic: parses JSON, resolves key/value aliases, writes SharedPreferences |
| `app/src/main/java/.../core/pref/fleet/FleetProfileActivity.kt` | Exported headless Activity — entry point for `am start` |
| `app/src/main/assets/fleet_profile_default.json` | Bundled default profile |
| `app/src/main/java/.../ui/main/drawer/DrawerFragment.kt` | Drawer UI — subtitle crash fix at line 593 |
| `docs/FLEET_PROFILE.md` | User-facing documentation |
| `docs/fleet/SUBTITLE_CRASH_BUG.md` | Analysis of the drawer crash (fixed) |
| `HANDOFF.md` | This file |
| `version.properties` | Version config — `VERSION_NAME`, `FLEET_BUILD_NUMBER`, etc. |
| `app/build.gradle.kts` | Build config — APK naming at lines 780-797 |

## Architecture

### Fleet Profile Flow

1. **FleetProfileActivity** receives intent via `am start` with `-e profile_path` or `-d` URI.
2. Delegates to **FleetProfileApplier.applyFromPath()** or **applyFromUri()**.
3. **applyProfile()** iterates JSON keys, skips `_`-prefixed meta keys:
   - Resolves human-readable key aliases (e.g. `"foreground_service"` → `key_$_foreground_service`) via `aliasToKey` map.
   - Resolves human-readable value aliases for `ListPreference` keys (e.g. `"alarm"` → `key_$_timed_task_backend_alarm`) via `valueAliasToKey` map.
   - Direct raw keys (`key_$_...`) pass through.
4. Writes to `SharedPreferences` via `Pref.get().edit()`.

### Result Delivery (all four fire on every invocation)

1. **Activity result intent** — extras for `startActivityForResult` callers.
2. **Broadcast** — action `org.autojs.autojs6.action.FLEET_PROFILE_RESULT` with same extras.
3. **JSON result file** — overwritten at `result_path` (default alongside profile or `/sdcard/autojs6-fleet-result.json`).
4. **Daily rotating Unix log** — appended to `/sdcard/autojs6-fleet-YYYY-MM-DD.log`. Format:
   ```
   2026-07-11T22:15:30Z INFO  fleet_profile: applied=12 skipped=0 keys=fg_service,stable_mode errors=0 message="Applied 12 preferences, skipped 0"
   2026-07-11T22:16:00Z WARN  fleet_profile: applied=5 skipped=3 failed=bad_key errors=1 message="Applied 5 preferences, skipped 3 (1 errors)"
   2026-07-11T22:17:00Z ERROR fleet_profile: applied=0 skipped=0 errors=1 message="Profile parse failed: invalid JSON"
   ```

### Safety guarantees

- **No UI** — `Theme.NoDisplay` in manifest, no `setContentView`, calls `finish()` immediately.
- **No Toasts** — zero `Toast` references in the activity.
- **No foreground** — no `startActivity` calls, no layouts.

### Key Design Decisions

- `valueAliasToKey` covers: timed_task_backend, scheduled_restart_backend, restart_strategy, night_mode, keep_screen_on_when_in_foreground, root_mode, hidden_files, file_extensions, documentation_source, app_language, launcher_icon, editor_pinch_to_zoom_strategy, root_record_out_file_type.
- `aliasToKey` covers ~25 common preferences. Both maps are hardcoded (deliberately — compile-time safe, no runtime resource lookup overhead).
- Drawer crash fix: bounds check at `DrawerFragment.kt:593` — `i in keys.indices` before array access.

## Releasing

### APK Naming Convention

```
autojs6-v{versionName}-stayturgid-{buildType}{buildNumber}-{architecture}.apk
```

Example: `autojs6-v6.7.0-fleet-profile-stayturgid-debug9-arm64-v8a.apk`

- `versionName` = `VERSION_NAME` from `version.properties` (currently `6.7.0-fleet-profile`)
- `buildType` = `debug` or `release` (auto-detected from variant)
- `buildNumber` = value of `FLEET_BUILD_NUMBER` in `version.properties`
- Architectures: `arm64-v8a`, `armeabi-v7a`, `armeabi`, `x86_64`, `x86`, `universal`

The APK manifest `versionName` matches the GitHub release tag (both `6.7.0-fleet-profile`), so Obtainium and other version-aware tooling can correctly detect the installed build.

### Build & Publish Steps

```bash
# 1. Bump build number
#    Edit version.properties: FLEET_BUILD_NUMBER = $((current + 1))

# 2. Build
./gradlew app:assembleAppDebug

# 3. Create GitHub release (not draft)
gh release create v6.7.0-fleet-profile \
    --repo djbclark/AutoJs6 \
    --title "v6.7.0-fleet-profile" \
    --notes "Short description of changes" \
    --target master \
    app/build/outputs/apk/app/debug/autojs6-v6.7.0-fleet-profile-stayturgid-debug*-*.apk

# 4. Commit and push
git add -A && git commit -m "chore: bump FLEET_BUILD_NUMBER to N" && git push fork master
```

All releases are published (not draft). The release tag is always `v6.7.0-fleet-profile` — replaced each time (delete + recreate).

## Known Issues

### HIGH PRIORITY, unresolved: `jvm-npm.js` redeclaration crash across sibling requires

`app/src/main/assets/modules/jvm-npm.js`'s rewritten `Module._load` dropped upstream jvm-npm's `new Function(exports, module, require, __filename, __dirname, body)` per-module isolation wrapper in favor of delegating to `NativeRequire.require(file)` (this app's own installed `commonjs.module.Require`, via `RhinoJavaScriptEngine.initRequireBuilder()`). Two required files that each declare a top-level `const`/`let` binding under the same name for a shared dependency (`const log = require("./log.js")`, say) crash the instant both load:

```
TypeError: redeclaration of var log. (jvm-npm.js#67)
```

Confirmed on-device (Pixel 7a). Root-cause investigation, minimal repro, and a working static-analysis workaround: https://github.com/djbclark/autojs6-typescript/tree/main/examples/broken/01-redeclaration

**This is entirely our own change, not an upstream jvm-npm limitation.** Checked upstream jvm-npm's source directly: `Module._load` there *always* uses the Function-wrapper for every successfully-resolved file — `NativeRequire.require` upstream is used in exactly one place, as a not-found/native-module fallback inside `Require()`'s `if (!file)` branch, never as a substitute for `Module._load`'s isolation. Our rewrite repurposed that fallback into the entire primary file-loading path on our own; there's no upstream design ambiguity to resolve.

Reported upstream: https://github.com/SuperMonster003/AutoJs6/issues/564 (a cross-filing on jvm-npm's own tracker was retracted once the above was confirmed — see the issue's comments).

Fix candidates: restore the Function-wrapper isolation in `Module._load` for file-based modules (matching upstream jvm-npm), or otherwise verify the `commonjs.module.Require` delegation path actually isolates `const`/`let` correctly and fix if not.

### Fixed: Drawer subtitle crash (commit `bcbf4390`)

See `docs/fleet/SUBTITLE_CRASH_BUG.md`. `DrawerFragment.refreshSubtitle` now checks `i in keys.indices` before array access.

### Fixed: Test coverage added (commit `3877e0fa`)

`FleetProfileApplierTest.kt` at `app/src/androidTest/java/.../fleet/FleetProfileApplierTest.kt` with 18 tests covering JSON parsing, alias resolution, value aliases, error handling, Result serialization, and file I/O. Run on device via `./gradlew app:connectedAppDebugAndroidTest`.

## External References

- **stayturgid** (`github.com/djbclark/stayturgid`) — fleet orchestration consuming this API. Uses `am start` fire-and-forget. Contains Shizuku catastrophic recovery watchdog (`docs/adr/003-shizuku-catastrophic-recovery.md`). Hit the jvm-npm redeclaration bug above in real multi-file TypeScript-compiled usage (`docs/architecture/components/autojs6.md`, "Rhino JS-engine gotchas").
- **autojs6-typescript** (`github.com/djbclark/autojs6-typescript`) — fork-agnostic TypeScript/Rhino gotcha catalog + verification toolkit; owns the canonical writeup and workaround for the jvm-npm redeclaration bug above.

## Potential Next Steps

- Accept custom `SharedPreferences` name in `FleetProfileApplier` for testing or multi-profile support.
- Use `FleetProfileApplier` internally at first launch to seed defaults for headless users.
- `LogBottomSheet` color resource fix (`9ad9f2c`) may need follow-up.
- The `aliasToKey` / `valueAliasToKey` maps were left hardcoded (compile-time safe, fast); resource-driven approach was deemed over-engineering.
